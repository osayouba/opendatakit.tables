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
package org.opendatakit.tables.Activity.util;

import java.io.File;
import org.opendatakit.tables.Activity.TableManager;
import org.opendatakit.tables.activities.Controller;
import org.opendatakit.tables.data.DbHelper;
import org.opendatakit.tables.data.KeyValueStore;
import org.opendatakit.tables.data.Preferences;
import org.opendatakit.tables.data.TableProperties;
import org.opendatakit.tables.view.custom.CustomView;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;


public class Launcher extends Activity {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ensuring directories exist
        String dir = Environment.getExternalStorageDirectory() + "/odk/tables";
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        // this should happen in another thread if possible
        CustomView.initCommonWebView(this);
        String tableId = (new Preferences(this)).getDefaultTableId();
        if (tableId == null) {
            Intent i = new Intent(this, TableManager.class);
            startActivity(i);
        } else {
            TableProperties tp = TableProperties.getTablePropertiesForTable(
                    DbHelper.getDbHelper(this), tableId, 
                    KeyValueStore.Type.ACTIVE);
            Controller.launchTableActivity(this, tp, true);
        }
        finish();
    }
}
