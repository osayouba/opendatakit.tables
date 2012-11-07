/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.tables.sync;

/**
 * A utility class for common synchronization methods and definitions.
 */
public class SyncUtil {

  /**
   * <p>
   * Synchronization state.
   * </p>
   * <p>
   * Here is a brief overview of the rules for transitions between states on
   * basic write operations:
   * 
   * <pre>
   * insert: 
   *     state = INSERTING
   *   
   * update:
   *     if state == REST:
   *        state = UPDATING
   *     
   * delete:
   *     if state == REST or state == UPDATING:
   *        state = DELETING
   *        don't actually delete yet
   *     else if state == INSERTING:
   *        actually delete
   * </pre>
   * 
   * </p>
   * <p>
   * The {@link SyncProcessor} handles moving resources from the INSERTING,
   * UPDATING, or DELETING states back to the REST state. CONFLICTING is a
   * special state set by the SyncProcessor to signify conflicts between local
   * and remote updates to the same resource and is handled separately from the
   * basic write operations.
   * 
   */
  public class State {
    public static final int REST = 0;
    public static final int INSERTING = 1;
    public static final int UPDATING = 2;
    public static final int DELETING = 3;
    public static final int CONFLICTING = 4;

    private State() {
    }
  }

  public static boolean intToBool(int i) {
    return i != 0;
  }

  public static int boolToInt(boolean b) {
    return b ? 1 : 0;
  }
  
  public static boolean stringToBool(String bool) {
    return bool.equalsIgnoreCase("true");
  }
  
}
