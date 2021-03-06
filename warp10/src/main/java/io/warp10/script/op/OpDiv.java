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

package io.warp10.script.op;

import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptNAryFunction;
import io.warp10.script.WarpScriptException;

import java.util.Map;

/**
 * Perform a division from two time series. The elevation and location are cleared.
 */
public class OpDiv extends NamedWarpScriptFunction implements WarpScriptNAryFunction {
  
  public OpDiv(String name) {
    super(name);
  }
  
  @Override
  public Object apply(Object[] args) throws WarpScriptException {
    long tick = (long) args[0];
    String[] names = (String[]) args[1];
    Map<String,String>[] labels = (Map<String,String>[]) args[2];
    long[] ticks = (long[]) args[3];
    long[] locations = (long[]) args[4];
    long[] elevations = (long[]) args[5];
    Object[] values = (Object[]) args[6];
    
    //
    // If there are more than two GTS, bail out
    //
    
    if (2 != values.length) {
      //throw new WarpScriptException("op.div can only be applied to two geo time series.");
      return new Object[] { tick, GeoTimeSerie.NO_LOCATION, GeoTimeSerie.NO_ELEVATION, null };
    }
    
    Object quotient = null;
    
    if (null != values[0] && null != values[1]) {
      if ((!(values[0] instanceof Long) && !(values[0] instanceof Double))
          || (!(values[1] instanceof Long) && !(values[1] instanceof Double))) {
        throw new WarpScriptException("op.div can only be applied to LONG or DOUBLE values.");
      }
       
      quotient = ((Number) values[0]).doubleValue() / ((Number) values[1]).doubleValue();  
    }

    long location = GeoTimeSerie.NO_LOCATION;
    long elevation = GeoTimeSerie.NO_ELEVATION;
    
    return new Object[] { tick, location, elevation, quotient };
  }
}
