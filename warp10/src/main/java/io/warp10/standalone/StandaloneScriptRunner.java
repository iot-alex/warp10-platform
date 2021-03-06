//
//   Copyright 2018  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.standalone;

import io.warp10.continuum.BootstrapManager;
import io.warp10.continuum.Configuration;
import io.warp10.continuum.TimeSource;
import io.warp10.continuum.geo.GeoDirectoryClient;
import io.warp10.continuum.sensision.SensisionConstants;
import io.warp10.continuum.store.Constants;
import io.warp10.continuum.store.DirectoryClient;
import io.warp10.continuum.store.StoreClient;
import io.warp10.crypto.CryptoUtils;
import io.warp10.crypto.KeyStore;
import io.warp10.crypto.OrderPreservingBase64;
import io.warp10.script.WarpScriptLib;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.MemoryWarpScriptStack;
import io.warp10.script.ScriptRunner;
import io.warp10.script.WarpScriptStack.StackContext;
import io.warp10.sensision.Sensision;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import com.geoxp.oss.CryptoHelper;
import com.geoxp.oss.client.OSSClient;
import com.google.common.base.Charsets;
import com.google.common.primitives.Longs;

public class StandaloneScriptRunner extends ScriptRunner {
  
  private final StoreClient storeClient;
  private final DirectoryClient directoryClient;
  private final GeoDirectoryClient geoDirectoryClient;
  private final Properties props;
  private final BootstrapManager bootstrapManager;

  private final Random prng = new Random();

  private final byte[] runnerPSK;
  
  public StandaloneScriptRunner(Properties properties, KeyStore keystore, StoreClient storeClient, DirectoryClient directoryClient, GeoDirectoryClient geoDirectoryClient, Properties props) throws IOException {
    super(keystore, props);

    this.props = props;
    this.directoryClient = directoryClient;
    this.geoDirectoryClient = geoDirectoryClient;
    this.storeClient = storeClient;
    
    //
    // Check if we have a 'bootstrap' property
    //
    
    if (properties.containsKey(Configuration.CONFIG_WARPSCRIPT_RUNNER_BOOTSTRAP_PATH)) {     
      final String path = properties.getProperty(Configuration.CONFIG_WARPSCRIPT_RUNNER_BOOTSTRAP_PATH);
      
      long period = properties.containsKey(Configuration.CONFIG_WARPSCRIPT_RUNNER_BOOTSTRAP_PERIOD) ?  Long.parseLong(properties.getProperty(Configuration.CONFIG_WARPSCRIPT_RUNNER_BOOTSTRAP_PERIOD)) : 0L ;
      this.bootstrapManager = new BootstrapManager(path, period);      
    } else {
      this.bootstrapManager = new BootstrapManager();
    }
    
    this.runnerPSK = keystore.getKey(KeyStore.AES_RUNNER_PSK);
  }
  
  @Override
  protected void schedule(final Map<String, Long> nextrun, final String script, final long periodicity) {
    
    try {
      
      final long scheduledat = System.currentTimeMillis();
      
      this.executor.submit(new Runnable() {            
        @Override
        public void run() {
          
          long nowts = System.currentTimeMillis();

          File f = new File(script);
          
          Map<String,String> labels = new HashMap<String,String>();
          //labels.put(SensisionConstants.SENSISION_LABEL_PATH, Long.toString(periodicity) + "/" + f.getName());
          String path = f.getAbsolutePath().substring(getRoot().length() + 1);
          labels.put(SensisionConstants.SENSISION_LABEL_PATH, path);
          
          Sensision.update(SensisionConstants.SENSISION_CLASS_EINSTEIN_RUN_COUNT, labels, 1);

          long nano = System.nanoTime();
          
          WarpScriptStack stack = new MemoryWarpScriptStack(storeClient, directoryClient, geoDirectoryClient, props);

          
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          
          try {            
            Sensision.update(SensisionConstants.SENSISION_CLASS_EINSTEIN_RUN_CURRENT, Sensision.EMPTY_LABELS, 1);

            InputStream in = new FileInputStream(f);
                        
            byte[] buf = new byte[1024];
            
            while(true) {
              int len = in.read(buf);
              
              if (len < 0) {
                break;
              }
              
              baos.write(buf, 0, len);
            }
            
            // Add a 'CLEAR' at the end of the script so we don't return anything
            baos.write(CLEAR);
            
            in.close();

            //
            // Replace the context with the bootstrap one
            //
            
            StackContext context = bootstrapManager.getBootstrapContext();
                  
            if (null != context) {
              stack.push(context);
              stack.restore();
            }
                  
            //
            // Execute the bootstrap code
            //

            stack.exec(WarpScriptLib.BOOTSTRAP);

            stack.store(Constants.RUNNER_PERIODICITY, periodicity);
            stack.store(Constants.RUNNER_PATH, path);
            stack.store(Constants.RUNNER_SCHEDULEDAT, scheduledat);
            
            //
            // Generate a nonce by wrapping the current time with random 64bits
            //
            
            if (null != runnerPSK) {
              byte[] now = Longs.toByteArray(TimeSource.getTime());
              
              byte[] nonce = CryptoHelper.wrapBlob(runnerPSK, now);
              
              stack.store(Constants.RUNNER_NONCE, new String(OrderPreservingBase64.encode(nonce), Charsets.US_ASCII));              
            }
            
            stack.execMulti(new String(baos.toByteArray(), Charsets.UTF_8));
          } catch (Exception e) {                
            Sensision.update(SensisionConstants.SENSISION_CLASS_EINSTEIN_RUN_FAILURES, labels, 1);
          } finally {
            nextrun.put(script, nowts + periodicity);
            nano = System.nanoTime() - nano;
            Sensision.update(SensisionConstants.SENSISION_CLASS_EINSTEIN_RUN_TIME_US, labels, (long) (nano / 1000L));
            Sensision.update(SensisionConstants.SENSISION_CLASS_EINSTEIN_RUN_ELAPSED, labels, nano); 
            Sensision.update(SensisionConstants.SENSISION_CLASS_EINSTEIN_RUN_OPS, labels, (long) stack.getAttribute(WarpScriptStack.ATTRIBUTE_OPS)); 
            Sensision.update(SensisionConstants.SENSISION_CLASS_EINSTEIN_RUN_FETCHED, labels, ((AtomicLong) stack.getAttribute(WarpScriptStack.ATTRIBUTE_FETCH_COUNT)).get());            
            Sensision.update(SensisionConstants.SENSISION_CLASS_EINSTEIN_RUN_CURRENT, Sensision.EMPTY_LABELS, -1);
          }              
        }
      });                  
    } catch (RejectedExecutionException ree) {
      // Reschedule script immediately
      nextrun.put(script, System.currentTimeMillis());
    }    
  }
}
