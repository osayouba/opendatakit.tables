/* * Copyright (C) 2013 University of Washington * * Licensed under the Apache License, Version 2.0 (the "License"); you may not * use this file except in compliance with the License. You may obtain a copy of * the License at * * http://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, software * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the * License for the specific language governing permissions and limitations under * the License. */package org.opendatakit.tables.activities;import java.util.ArrayList;import java.util.HashMap;import java.util.List;import java.util.Map;import org.opendatakit.tables.R;import org.opendatakit.tables.activities.importexport.ImportExportActivity;import org.opendatakit.tables.data.DbHelper;import org.opendatakit.tables.data.KeyValueStore;import org.opendatakit.tables.data.Preferences;import org.opendatakit.tables.data.TableProperties;import org.opendatakit.tables.data.TableType;import org.opendatakit.tables.tasks.InitializeTask;import org.opendatakit.tables.utils.ConfigurationUtil;import org.opendatakit.tables.utils.NameUtil;import android.app.AlertDialog;import android.content.Context;import android.content.DialogInterface;import android.content.Intent;import android.os.Bundle;import android.util.Log;import android.view.ContextMenu;import android.view.ContextMenu.ContextMenuInfo;import android.view.LayoutInflater;import android.view.View;import android.view.View.OnClickListener;import android.view.ViewGroup;import android.view.WindowManager;import android.widget.AdapterView;import android.widget.EditText;import android.widget.ImageView;import android.widget.ListView;import android.widget.SimpleAdapter;import android.widget.TextView;import android.widget.Toast;import com.actionbarsherlock.app.SherlockListActivity;import com.actionbarsherlock.view.Menu;import com.actionbarsherlock.view.MenuItem;import com.actionbarsherlock.view.SubMenu;public class TableManager extends SherlockListActivity implements     InitializeTask.Callbacks {    private static final String TAG = TableManager.class.getSimpleName();	private static final String TABLE_MANAGER_ROW_MAP_KEY_LABEL = "label";	private static final String SEMICOLON_SPACE = "; ";	private static final String TABLE_MANAGER_ROW_MAP_KEY_EXT = "ext";	public static final int ADD_NEW_TABLE     		= 1;	public static final int ADD_NEW_SECURITY_TABLE 	= 2;	public static final int IMPORT_EXPORT			= 3;	public static final int SET_DEFAULT_TABLE 		= 4;	public static final int SET_SECURITY_TABLE      = 5;	public static final int SET_SHORTCUT_TABLE      = 6;	public static final int REMOVE_TABLE      		= 7;	public static final int ADD_NEW_SHORTCUT_TABLE  = 8;	public static final int UNSET_DEFAULT_TABLE     = 9;	public static final int UNSET_SECURITY_TABLE    = 10;	public static final int UNSET_SHORTCUT_TABLE    = 11;	public static final int AGGREGATE               = 12;	public static final int LAUNCH_TPM              = 13;	public static final int LAUNCH_CONFLICT_MANAGER = 14;	public static final int LAUNCH_DPREFS_MANAGER   = 15;	public static final int LAUNCH_SECURITY_MANAGER = 16;	private static String[] from = new String[] {TABLE_MANAGER_ROW_MAP_KEY_LABEL, TABLE_MANAGER_ROW_MAP_KEY_EXT, "options"};	private static int[] to = new int[] { android.R.id.text1, android.R.id.text2, R.id.row_settings };	private List<Map<String, String>> fMaps;	private DbHelper dbh;	private Preferences prefs;	private TableProperties[] tableProps;	private SimpleAdapter arrayAdapter;	@Override	public void onCreate(Bundle savedInstanceState) {		super.onCreate(savedInstanceState);		dbh = DbHelper.getDbHelper(this);		prefs = new Preferences(this);		// Remove title of activity		setTitle("");		// Set Content View		setContentView(R.layout.plain_list);		init();		refreshList();	}	/**	 * initializes TableManager by importing csv files listed in the	 * config.properties file	 */	private void init() {		if (ConfigurationUtil.isChanged(prefs)) {			new InitializeTask(this, this).execute();		}	}	/**	 * Gets the Preferences for TableManager	 */	public Preferences getPrefs() {		return prefs;	}	@Override	public void onResume() {		super.onResume();		refreshList();	}	private void makeNoTableNotice() {		List<HashMap<String, String>> fillMaps = new ArrayList<HashMap<String, String>>();		HashMap<String, String> temp = new HashMap<String, String>();		temp.put(TABLE_MANAGER_ROW_MAP_KEY_LABEL, getString(R.string.no_table_message));		fillMaps.add(temp);		arrayAdapter = new SimpleAdapter(this, fillMaps, R.layout.plain_list_row, from, to);		setListAdapter(arrayAdapter);	}	/**	 * re-populates the Table Manager's List View and sets the onClickListener for	 * the settings icon on the right each row	 */	// (no longer necessary to long-click on the row to get the context menu)	public void refreshList() {		registerForContextMenu(getListView());		tableProps = TableProperties.getTablePropertiesForAll(dbh,				KeyValueStore.Type.ACTIVE);		Log.d("TM", "refreshing list, tableProps.length=" + tableProps.length);		if (tableProps.length == 0) {			makeNoTableNotice();			return;		}		String defTableId = prefs.getDefaultTableId();		fMaps = new ArrayList<Map<String, String>>();		for(TableProperties tp : tableProps) {			Map<String, String> map = new HashMap<String, String>();			map.put(TABLE_MANAGER_ROW_MAP_KEY_LABEL, tp.getDisplayName());			if (tp.getTableType() == TableType.security) {				map.put(TABLE_MANAGER_ROW_MAP_KEY_EXT, getString(R.string.table_type_access_control));			} else if (tp.getTableType() == TableType.shortcut) {				map.put(TABLE_MANAGER_ROW_MAP_KEY_EXT, getString(R.string.table_type_sms_shortcuts));			}			if(tp.getTableId() == defTableId) {				if(map.get(TABLE_MANAGER_ROW_MAP_KEY_EXT) == null) {					map.put(TABLE_MANAGER_ROW_MAP_KEY_EXT, getString(R.string.table_is_default_table));				} else {					map.put(TABLE_MANAGER_ROW_MAP_KEY_EXT, map.get(TABLE_MANAGER_ROW_MAP_KEY_EXT) +							SEMICOLON_SPACE + getString(R.string.table_is_default_table));				}			}			fMaps.add(map);		}		// fill in the grid_item layout		arrayAdapter = new RowAdapter();		setListAdapter(arrayAdapter);		// clicking the row name opens that table		ListView lv = getListView();		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {			@Override			public void onItemClick(AdapterView<?> adView, View view,					int position, long id) {				// Load Selected Table				loadSelectedTable(position);			}		});		// Disable the ability to open a contextual menu by long clicking on the		// table name (so only the settings icon can open the contextual menu)		//		lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {		//		//			@Override		//			public boolean onItemLongClick(AdapterView<?> arg0, View arg1,		//					int arg2, long arg3) {		//				return true;		//			}		//		//		});	}	private void loadSelectedTable(int index) {		TableProperties tp = tableProps[index];		Controller.launchTableActivity(this, tp, true);	}	@Override	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {		super.onCreateContextMenu(menu, v, menuInfo);		AdapterView.AdapterContextMenuInfo acmi =				(AdapterView.AdapterContextMenuInfo) menuInfo;		TableProperties tp = tableProps[acmi.position];		if(tp.getTableId().equals(prefs.getDefaultTableId())) {			menu.add(0, UNSET_DEFAULT_TABLE, 0, getString(R.string.unset_as_default_table));		} else {			menu.add(0, SET_DEFAULT_TABLE, 0, getString(R.string.set_as_default_table));		}		TableType tableType = tp.getTableType();		if (tableType == TableType.data) {			if (couldBeSecurityTable(tp)) {				menu.add(0, SET_SECURITY_TABLE, 0, getString(R.string.set_as_access_control_table));			}			if (couldBeShortcutTable(tp)) {				menu.add(0, SET_SHORTCUT_TABLE, 0, getString(R.string.set_as_shortcut_table));			}		} else if (tableType == TableType.security) {			menu.add(0, UNSET_SECURITY_TABLE, 0, getString(R.string.unset_as_access_control_table));		} else if (tableType == TableType.shortcut) {			menu.add(0, UNSET_SHORTCUT_TABLE, 0, getString(R.string.unset_as_shortcut_table));		}		menu.add(0, REMOVE_TABLE, 1, getString(R.string.delete_table));		menu.add(0, LAUNCH_TPM, 2, getString(R.string.edit_table_props));		menu.add(0, LAUNCH_CONFLICT_MANAGER, 3, getString(R.string.manage_conflicts));		menu.add(0, LAUNCH_SECURITY_MANAGER, 4, getString(R.string.security_manager));	}	private boolean couldBeSecurityTable(TableProperties tp) {		String[] expected = { "phone_number", "id", "password" };		return checkTable(expected, tp);	}	private boolean couldBeShortcutTable(TableProperties tp) {		String[] expected = { "name", "input_format", "output_format" };		return checkTable(expected, tp);	}	private boolean checkTable(String[] expectedCols, TableProperties tp) {		if (tp.getColumns().size() < expectedCols.length) {			return false;		}		for (int i = 0; i < expectedCols.length; i++) {		  if (tp.getColumnByElementKey(expectedCols[i]) == null) {		    return false;		  }		}		return true;	}	public boolean onContextItemSelected(android.view.MenuItem item) {		AdapterView.AdapterContextMenuInfo info= (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();		final TableProperties tp = tableProps[info.position];		switch (item.getItemId()) {		case SET_DEFAULT_TABLE:			prefs.setDefaultTableId(tp.getTableId());			refreshList();			return true;		case UNSET_DEFAULT_TABLE:			prefs.setDefaultTableId(null);			refreshList();			return true;		case SET_SECURITY_TABLE:			tp.setTableType(TableType.security);			refreshList();			return true;		case UNSET_SECURITY_TABLE:			tp.setTableType(TableType.data);			refreshList();			return true;		case SET_SHORTCUT_TABLE:			tp.setTableType(TableType.shortcut);			refreshList();			return true;		case UNSET_SHORTCUT_TABLE:			tp.setTableType(TableType.data);			refreshList();			return true;		case REMOVE_TABLE:			AlertDialog confirmDeleteAlert;			// Prompt an alert box			AlertDialog.Builder alert =			new AlertDialog.Builder(TableManager.this);			alert.setTitle(getString(R.string.confirm_delete_table))			.setMessage(getString(R.string.are_you_sure_delete_table, tp.getDisplayName()));			// OK Action => delete the row			alert.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {				public void onClick(DialogInterface dialog, int whichButton) {					tp.deleteTable();					refreshList();				}			});			// Cancel Action			alert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {				public void onClick(DialogInterface dialog, int whichButton) {					// Canceled.				}			});			// show the dialog			confirmDeleteAlert = alert.create();			confirmDeleteAlert.show();			return true;		case LAUNCH_TPM:		{			Intent i = new Intent(this, TablePropertiesManager.class);			i.putExtra(TablePropertiesManager.INTENT_KEY_TABLE_ID, tp.getTableId());			startActivity(i);			return true;		}		case LAUNCH_CONFLICT_MANAGER:		{// THE OLD CONFLICT RESOLUTION STUFF		  //			Intent i = new Intent(this, ConflictResolutionActivity.class);//			i.putExtra(Controller.INTENT_KEY_TABLE_ID, tp.getTableId());//			i.putExtra(Controller.INTENT_KEY_IS_OVERVIEW, false);//			startActivity(i);//			return true;		  Intent i = new Intent(this, ConflictResolutionListActivity.class);		  i.putExtra(Controller.INTENT_KEY_TABLE_ID, tp.getTableId());		  startActivity(i);		  return true;		}		case LAUNCH_SECURITY_MANAGER:		{			Intent i = new Intent(this, SecurityManager.class);			i.putExtra(SecurityManager.INTENT_KEY_TABLE_ID, tp.getTableId());			startActivity(i);			return true;		}		}		return(super.onOptionsItemSelected(item));	}	// CREATE OPTION MENU	@Override	public boolean onCreateOptionsMenu(Menu menu) {		super.onCreateOptionsMenu(menu);		// Sub-menu containing different "add" menus		SubMenu addNew = menu.addSubMenu(getString(R.string.add_new_table));		addNew.setIcon(R.drawable.content_new);		MenuItem subMenuItem = addNew.getItem();		subMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);		addNew.add(0, ADD_NEW_TABLE, 0, getString(R.string.add_new_data_table));		// Commented these out for demo purposes, as their actions are currently		// undefined.		//addNew.add(0, ADD_NEW_SECURITY_TABLE, 0, getString(R.string.add_new_access_control_table));		//addNew.add(0, ADD_NEW_SHORTCUT_TABLE, 0, getString(R.string.add_new_shortcuts_table));		addNew.add(0, IMPORT_EXPORT, 0, getString(R.string.file_import_export_menu_item));		MenuItem item;		item = menu.add(0, AGGREGATE, 0, getString(R.string.sync_menu_item));		item.setIcon(R.drawable.sync_icon);		item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);		item = menu.add(0, LAUNCH_DPREFS_MANAGER, 0, getString(R.string.display_prefs));		item.setIcon(R.drawable.settings_icon2);		item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);		return true;	}	// HANDLE OPTION MENU	@Override	public boolean onMenuItemSelected(int featureId, MenuItem item) {		Log.d("timing", "menu item selected");		// HANDLES DIFFERENT MENU OPTIONS		switch(item.getItemId()) {		case 0:			return true;		case ADD_NEW_TABLE:			alertForNewTableName(true, TableType.data, null, null);			return true;		case ADD_NEW_SECURITY_TABLE:			alertForNewTableName(true, TableType.security, null, null);			return true;		case ADD_NEW_SHORTCUT_TABLE:			alertForNewTableName(true, TableType.shortcut, null, null);			return true;		case IMPORT_EXPORT:			Intent i = new Intent(this, ImportExportActivity.class);			startActivity(i);			return true;		case AGGREGATE:			Intent j = new Intent(this, Aggregate.class);			startActivity(j);			return true;		case LAUNCH_DPREFS_MANAGER:			Intent k = new Intent(this, DisplayPrefsActivity.class);			startActivity(k);			return true;		}		return super.onMenuItemSelected(featureId, item);	}	// Ask for a new table name.	/*	 * Note that when prompting for a new data table, the following parameters	 * are passed to the method:	 * isNewTable == true	 * tableType == data	 * tp == null	 * givenTableName == null	 */	private void alertForNewTableName(final boolean isNewTable,			final TableType tableType, final TableProperties tp, 			String givenTableName) {		AlertDialog newTableAlert;		// Prompt an alert box		AlertDialog.Builder alert = new AlertDialog.Builder(this);	    View view = getLayoutInflater()	        .inflate(R.layout.message_with_text_edit_field_dialog, null);	    alert.setView(view)		.setTitle(R.string.create_new_table);	    final TextView msg = (TextView) view.findViewById(R.id.message);	    msg.setText(getString(R.string.enter_new_table_name));		// Set an EditText view to get user input		final EditText input = (EditText) view.findViewById(R.id.edit_field);		if (givenTableName != null)			input.setText(givenTableName);		// OK Action => Create new Column		alert.setPositiveButton(getString(R.string.ok), 		    new DialogInterface.OnClickListener() {			public void onClick(DialogInterface dialog, int whichButton) {				String newTableName = input.getText().toString().trim();				if (newTableName == null || newTableName.equals("")) {					// Table name is empty string					toastTableNameError(getString(R.string.error_table_name_empty));					alertForNewTableName(isNewTable, tableType, tp, null);				} else {					if (isNewTable)						alertForTableId(isNewTable, tableType, tp, 						    newTableName, null);					else						tp.setDisplayName(newTableName);					Log.d("TM", "got here");					refreshList();				}			}		});		// Cancel Action		alert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {			public void onClick(DialogInterface dialog, int whichButton) {				// Canceled.			}		});		newTableAlert = alert.create();		newTableAlert.getWindow().setSoftInputMode(WindowManager.				LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);		newTableAlert.show();	}		/**	 * Following the entering of a table display name a dialog should be 	 * presented that suggests a table id and offers the user a chance to change	 * it. Only upon accepting the name here is the table created.	 * <p>	 * The first call can include a proposedTableId of null.	 * @param isNewTable	 * @param tableType	 * @param tp	 * @param proposedDisplayName	 * @param proposedTableId	 */	private void alertForTableId(final boolean isNewTable, 	    final TableType tableType, final TableProperties tp, 	    final String proposedDisplayName, String proposedTableId) {     AlertDialog newTableAlert;     // Prompt an alert box     AlertDialog.Builder alert = new AlertDialog.Builder(this);      View view = getLayoutInflater()          .inflate(R.layout.message_with_text_edit_field_dialog, null);      alert.setView(view)     .setTitle(R.string.create_new_table);      final TextView msg = (TextView) view.findViewById(R.id.message);      msg.setText(getString(R.string.confirm_new_table_id));     // Set an EditText view to get user input     final EditText input = (EditText) view.findViewById(R.id.edit_field);         // Try to create the id.      if (proposedTableId == null) {       proposedTableId = proposedDisplayName;     }     final String knownSafeId =          NameUtil.createUniqueTableId(proposedDisplayName, tableProps);     input.setText(knownSafeId);     Log.d(TAG, "[alertForTableId] received: " + proposedTableId + " as " +     		"proposed id, known safeId is: " + knownSafeId);          // OK Action => Create new Column     alert.setPositiveButton(getString(R.string.ok),          new DialogInterface.OnClickListener() {        public void onClick(DialogInterface dialog, int whichButton) {           String proposedTableId = input.getText().toString().trim();           if (proposedTableId == null || proposedTableId.equals("")) {              // Table name is empty string              toastTableNameError(getString(R.string.error_table_name_empty));              alertForNewTableName(isNewTable, tableType, tp, null);           } else if (               !NameUtil.isValidUserDefinedDatabaseName(proposedTableId)) {             Log.d(TAG, "[alertForTableId] proposed is is not valid " +             		"as a user-defined database name: " + proposedTableId);             toastTableNameError(getString(R.string.error_table_id_invalid));             alertForTableId(isNewTable, tableType, tp, proposedDisplayName,                 knownSafeId);           } else if (               NameUtil.tableIdAlreadyExists(proposedTableId, tableProps)) {             // Then we've got a name conflict.             Log.d(TAG, "[alertForTableId] name conflict on: "                + proposedTableId);             toastTableNameError(getString(R.string.error_table_id_exists));             alertForTableId(isNewTable, tableType, tp, proposedDisplayName,                 knownSafeId);           } else {              if (isNewTable)                 addTable(proposedDisplayName, proposedTableId, tableType);              else                tp.setTableId(proposedTableId);              refreshList();           }        }     });     // Cancel Action     alert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {        public void onClick(DialogInterface dialog, int whichButton) {           // Canceled.        }     });     newTableAlert = alert.create();     newTableAlert.getWindow().setSoftInputMode(WindowManager.           LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);     newTableAlert.show();	  	}	private void toastTableNameError(String msg) {		Context context = getApplicationContext();		Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);		toast.show();	}	private void addTable(String displayName, String tableId, 	    TableType tableType) {	  String dbTableName = NameUtil.createUniqueDbTableName(displayName, 	      tableProps);		// If you're adding through the table manager, you're using the phone,		// and consequently you should be adding to the active store.		@SuppressWarnings("unused")		TableProperties tp = TableProperties.addTable(dbh, null, dbTableName,		    displayName, tableType, tableId, KeyValueStore.Type.ACTIVE);//		TableProperties tp = TableProperties.addTable(dbh, dbTableName,//				dbTableName, tableName, tableType, KeyValueStore.Type.ACTIVE);	}	public void contextualSettingsClicked(View view) {		openContextMenu(view);	}	class RowAdapter extends SimpleAdapter {		RowAdapter() {			super(TableManager.this, fMaps, R.layout.plain_list_row, from, to);		}		public View getView(int position, View convertView, ViewGroup parent) {			View row = convertView;			if (row == null) {				LayoutInflater inflater=getLayoutInflater();				row = inflater.inflate(R.layout.plain_list_row, parent, false);			}			// Current Position in the List			final int currentPosition = position;			Map<String, String> currentTable = fMaps.get(position);			// Register name of table at each row in the list view			TextView label = (TextView)row.findViewById(android.R.id.text1);			label.setText(currentTable.get(TABLE_MANAGER_ROW_MAP_KEY_LABEL));			// Register ext info for table			TextView ext = (TextView)row.findViewById(android.R.id.text2);			ext.setText(currentTable.get(TABLE_MANAGER_ROW_MAP_KEY_EXT));			// Settings icon opens a context menu for that table			final ImageView settingsIcon = (ImageView)row.findViewById(R.id.row_settings);			settingsIcon.setClickable(true);			settingsIcon.setOnClickListener(new OnClickListener() {				@Override				public void onClick(View v) {					openContextMenu(v);				}			});			return(row);		}	}  @Override  public void onImportsComplete() {    // Just refresh the list.    this.refreshList();  }}