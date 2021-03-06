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

package io.warp10.script.functions;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;

import java.util.List;
import java.util.Map;

/**
 * Extracts a value from a map or list given a key.
 */
public class GET extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public GET(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object key = stack.pop();
    
    Object coll = stack.pop();

    if (!(coll instanceof Map) && !(coll instanceof List) && !(coll instanceof byte[])) {
      throw new WarpScriptException(getName() + " operates on a map, list or byte array.");
    }
    
    Object value = null;
    
    if (coll instanceof Map) {
      value = ((Map) coll).get(key);
    } else {
      if (!(key instanceof Number)) {
        throw new WarpScriptException(getName() + " expects the key to be an integer when operating on a list.");
      }
      if (coll instanceof List) {
        value = ((List) coll).get(((Number) key).intValue());
      } else {
        value = (long) (((byte[]) coll)[((Number) key).intValue()] & 0xFFL);
      }
    }
    
    stack.push(value);

    return stack;
  }
}
