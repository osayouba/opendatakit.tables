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
package org.opendatakit.hope.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.opendatakit.aggregate.odktables.rest.entity.OdkTablesKeyValueStoreEntry;
import org.opendatakit.common.android.provider.ColumnDefinitionsColumns;
import org.opendatakit.hope.sync.SyncUtil;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * A class for accessing and managing column properties.
 * <p>
 * Column properties are located in several places. The more, although not
 * completely, immutable properties are located in a table that is defined in
 * {@link ColumnDefinitions}. The mutable, mostly ODK Tables-specific columns
 * are located in {@link KeyValueStoreColumn}. ColumnProperties is this an
 * abstraction of both of these.
 * <p>
 * It is the column analogue of {@link TableProperties}.
 * <p>
 * In the future, it might make sense to have TableProperties and
 * ColumnProperties share a common parent class, as they essentially have the
 * same functionality. Same thing for TableDefinitions and ColumnDefinitions.
 *
 * @author hkworden@gmail.com (Hilary Worden)
 * @author sudar.sam@gmail.com
 */
public class ColumnProperties {

  private static final ObjectMapper mapper;
  static {
    mapper = new ObjectMapper();
    mapper.setVisibilityChecker(mapper.getVisibilityChecker().withFieldVisibility(Visibility.ANY));
  }

  private static final String TAG = "ColumnProperties";

  // the name of the column properties table in the database
  // private static final String DB_TABLENAME = "column_definitions";
  // names of columns in the column properties table
  // private static final String DB_TABLE_ID = "table_id";
  // display attributes

  // public static final String DB_ELEMENT_KEY =
  // "element_key";// (was DB_DB_COLUMN_NAME)
  /*
   * (was dbColumnName) unique id for this element. There should be only one
   * such elementKey for a given tableId. This is the dbColumnName if it is a
   * value persisted into the database
   */
  // private static final String DB_ELEMENT_NAME = "elementName";
  /*
   * name for this element. Either the elementKey or the name of the element
   * within its enclosing composite type (element name within a struct). This is
   * therefore not unique within a table row, as there could be multiple entries
   * with 'latitude' as their element names.
   */
  // private static final String DB_ELEMENT_TYPE =
  // "element_type"; // (was DB_COL_TYPE)
  /* This is a string value. see larger comment below */
  // private static final String DB_LIST_CHILD_ELEMENT_KEYS =
  // "list_child_element_keys";
  /*
   * if this is a composite type (geopoint), this is a JSON list of the element
   * keys of the direct descendants of this field name.
   */
  // private static final String DB_IS_PERSISTED =
  // "is_persisted";
  /*
   * default: 1 (true) -- whether or not this is persisted to the database. If
   * true, elementId is the dbColumnName in which this value is written. The
   * value will be written as JSON if it is a composite type. see larger comment
   * below for example.
   */
  /*
   * These two columns have been replaced by the single column DB_JOINS
   */
  // private static final String DB_JOINS = "joins";
  // private static final String DB_JOIN_TABLE_ID = "joinTableId"; /* tableId of
  // table to join against */
  // private static final String DB_JOIN_ELEMENT_KEY = "joinElementKey"; // (was
  // DB_JOIN_COLUMN_NAME)
  /*
   * elementKey of the value to join (this table's element) against in other
   * table
   */
  /*
   * Data types and how things are stored:
   *
   * We have these primitive elementTypes: STRING INTEGER DECIMAL DATE DATETIME
   * TIME BOOLEAN MIMEURI and this composite type: MULTIPLE_CHOICE for multiple
   * choice options (arrays). These could hold any data type, but initial
   * implementation is only for STRING
   *
   * Anything else is user-specified strings that are used to identify
   * struct-like datatype definitions. Initially, this would be 'geopoint';
   * Tables would add 'phonenumber', 'date range'
   *
   * e.g., multiple-choice list of symptoms:
   *
   * The data is stored under the 'patientSymptoms' column in the database as a
   * JSON encoding of string values. i.e., '["ache","fever"]'
   *
   * ekey: patientSymptoms ename: patientSymptoms etype: MULTIPLE_CHOICE
   * listChildEKeys: '[ patientSymptomsItem ]' isPersist: true
   *
   * ekey: patientSymptomsItem ename: null // elements are not named within this
   * list etype: STRING listChildEKeys: null isPersist: false
   *
   * ------------- e.g., geopoint defining a northernmost point of something:
   *
   * The data is stored as 4 columns, 'northLatitude', 'northLongitude',
   * 'northAltitude', 'northAccuracy'
   *
   * ekey: northernmostPoint ename: northernmostPoint etype: geopoint
   * listChildEKeys: '[ "northLatitude", "northLongitude", "northAltitude",
   * "northAccuracy"]' isPersist: false
   *
   * ekey: northLatitude ename: latitude etype: DECIMAL listChildEKeys: null
   * isPersist: true
   *
   * ekey: northLongitude ename: longitude etype: DECIMAL listChildEKeys: null
   * isPersist: true
   *
   * ekey: northAltitude ename: altitude etype: DECIMAL listChildEKeys: null
   * isPersist: true
   *
   * ekey: northAccuracy ename: accuracy etype: DECIMAL listChildEKeys: null
   * isPersist: true
   *
   * ODK Collect can do calculations and constraint tests like
   * 'northermostPoint.altitude < 4.0'
   *
   * e.g., 'clientPhone' as a phonenumber type, which is just a restriction on a
   * STRING value persists under 'clientPhone' column in database.
   *
   * ekey: clientPhone ename: clientPhone etype: phoneNumber listChildEKeys: [
   * "clientPhoneNumber" ] // single element isPersist: true
   *
   * ekey: clientPhoneNumber ename: null // null -- indicates restriction on
   * etype etype: STRING listChildEKeys: null isPersist: false
   *
   * e.g., 'image' file capture in ODK Collect. Stored as a MIMEURI
   *
   * ekey: locationImage ename: locationImage etype: MIMEURI listChildEKeys:
   * null isPersist: true
   *
   * MIMEURI stores a JSON object:
   *
   * '{"path":"/mnt/sdcard/odk/tables/app/instances/2342.jpg","mimetype":"image/jpg"}'
   *
   * i.e., ODK Collect image/audio/video capture store everything as a MIMEURI
   * with different mimetype values.
   */

  /*
   *
   * NOTE: you can have a composite type stored in two ways: (1) store the leaf
   * nodes of the composite type in the database. Describe the entire type
   * hierarchy down to those leaf nodes. (2) store it as a json object at the
   * top level. Describe the structure of this json object and its leaf nodes
   * (but none of these persist anything).
   *
   * Each has its advantages -- (1) does independent value updates easily. (2)
   * does atomic updates easily.
   */
  // private static final String DB_DISPLAY_VISIBLE = "displayVisible";//
  // boolean (stored as Integer)
  /*
   * 1 as boolean (true) is this column visible in Tables [may want tristate -1
   * = deleted, 0 = hidden, 1 = visible?]
   */
  // private static final String DB_DISPLAY_NAME = "displayName"; /* perhaps as
  // json i18n */
  // private static final String DB_DISPLAY_CHOICES_MAP = "displayChoicesMap";
  // /* (was mcOptions)
  // choices i18n structure (Java needs rework).
  // TODO: allocate large storage on Aggregate
  /*
   * displayChoicesMap -- TODO: rework ( this is still an ArrayList<String> )
   *
   * This is a map used for select1 and select choices, either closed-universe
   * (fixed set) or open-universe (select1-or-other, select-or-other). Stores
   * the full list of all values in the column. Example format (1st label shows
   * localization, 2nd is simple single-language defn:
   *
   * [ { "name": "1", "label": { "fr" : "oui", "en" : "yes", "es" : "si" } }, {
   * "name" : "0", "label": "no" } ]
   *
   * an open-universe list could just be a list of labels:
   *
   * [ "yes", "oui", "si", "no" ]
   *
   * i.e., there is no internationalization possible in open-universe lists, as
   * we allow free-form text entry. TODO: is this how we want this to work.
   *
   * When a user chooses to enter their own data in the field, we add that entry
   * to this list for later display as an available choice (i.e., we update the
   * choices list).
   *
   * TODO: how to define open vs. closed universe treatment? TODO: generalize
   * for other data types? i.e., "name" as a date range?
   */
  // private static final String DB_DISPLAY_FORMAT = "displayFormat";
  /*
   * (FUTURE USE) format descriptor for this display column. e.g., In the
   * Javascript, we have 'handlebars helpers' for template generation. We could
   * share a subset of this functionality in Tables for managing how to render a
   * value.
   *
   * TODO: how does this interact with displayChoicesMap?
   *
   * The proposed eventual subset describes numeric formatting. It could also be
   * used to render qrcode images, etc. 'this' and elementName both refer to
   * this display value. E.g., sample usage syntax: "{{toFixed this "2"}}", //
   * this.toFixed(2) "{{toExponential this "2"}}" // this.toExponential(2)
   * "{{toPrecision this "2"}}" // this.toPrecision(2) "{{toString this "16"}}".
   * // this.toString(16) otherwise, it does {{this}} substitutions for
   * composite types. e.g., for geopoint:
   * "({{toFixed this.latitude "2"}}, {{toFixed this.longitude "
   * 2"}) {{toFixed this.altitude "1"}}m error: {{toFixed this.accuracy "1"}}m"
   * to produce '(48.50,32.20) 10.3m error: 6.0m'
   *
   * The only helper functions envisioned are "toFixed", "toExponential",
   * "toPrecision", "toString" and "localize" and perhaps one for qrcode
   * generation?
   *
   * TODO: how do you work with MULTIPLE_CHOICE e.g., for item separators (','
   * with final element ', and ')
   */

  // /***********************************
  // * Default values for those columns that have defaults.
  // ***********************************/
  // public static final int DEFAULT_DB_IS_PERSISTED = 1;

  /***********************************
   * The partition name of the column keys in the key value store.
   ***********************************/
  public static final String KVS_PARTITION = "Column";

  /***********************************
   * The names of keys that are defaulted to exist in the column key value
   * store.
   ***********************************/
  public static final String KEY_DISPLAY_VISIBLE = "displayVisible";
  /*
   * Integer, non null. 1: visible 0: not visible -1: deleted (even necessary?)
   */
  public static final String KEY_DISPLAY_NAME = "displayName";
  /*
   * Text, not null. Must be input when adding a column as they all must have a
   * display name.
   */
  public static final String KEY_DISPLAY_CHOICES_MAP = "displayChoicesMap";
  /*
   * Text, null.
   */
  public static final String KEY_DISPLAY_FORMAT = "displayFormat";
  /*
   * Text, null. Fot future use.
   */
  public static final String KEY_SMS_IN = "smsIn";
  /*
   * Integer, not null. As boolean. Allow incoming SMS to modify the column.
   */
  public static final String KEY_SMS_OUT = "smsOut";
  /*
   * Integer, not null. As boolean. Allow outgoing SMS to access this column.
   */
  public static final String KEY_SMS_LABEL = "smsLabel";
  /*
   * Text null.
   */
  public static final String KEY_FOOTER_MODE = "footerMode";
  /*
   * What the footer should display.
   */

  /***********************************
   * Default values for those keys which require them. TODO When the keys in the
   * KVS are moved to the respective classes that use them, these should go
   * there most likely.
   ***********************************/
  public static final boolean DEFAULT_KEY_VISIBLE = true;
  public static final FooterMode DEFAULT_KEY_FOOTER_MODE = FooterMode.none;
  public static final boolean DEFAULT_KEY_SMS_IN = true;
  public static final boolean DEFAULT_KEY_SMS_OUT = true;
  public static final String DEFAULT_KEY_SMS_LABEL = null;
  public static final String DEFAULT_KEY_DISPLAY_FORMAT = null;
  public static final ArrayList<String> DEFAULT_KEY_DISPLAY_CHOICES_MAP = new ArrayList<String>();

  /***********************************
   * Keys for json.
   ***********************************/

  // private static final String DB_SMS_IN = "smsIn"; /* (allow SMS incoming)
  // default: 1 as boolean (true) */
  // private static final String DB_SMS_OUT = "smsOut"; /* (allow SMS outgoing)
  // default: 1 as boolean (true) */
  // private static final String DB_SMS_LABEL = "smsLabel"; /* for SMS */

  // private static final String DB_FOOTER_MODE = "footerMode"; /* 0=none,
  // 1=count, 2=minimum, 3=maximum, 4=mean, 5=sum */

  // keys for JSON
  private static final String JSON_KEY_VERSION = "jVersion";
  private static final String JSON_KEY_TABLE_ID = "tableId";

  public static final String JSON_KEY_ELEMENT_KEY = "elementKey";// (was
                                                                 // dbColumnName)
  public static final String JSON_KEY_ELEMENT_NAME = "elementName";
  private static final String JSON_KEY_ELEMENT_TYPE = "elementType"; // (was
                                                                     // colType)
  private static final String JSON_KEY_LIST_CHILD_ELEMENT_KEYS = "listChildElementKeys";
  private static final String JSON_KEY_JOINS = "joins";
  private static final String JSON_KEY_IS_PERSISTED = "isPersisted";

  private static final String JSON_KEY_DISPLAY_VISIBLE = "displayVisible";
  private static final String JSON_KEY_DISPLAY_NAME = "displayName";
  private static final String JSON_KEY_DISPLAY_CHOICES_MAP = "displayChoicesMap";
  private static final String JSON_KEY_DISPLAY_FORMAT = "displayFormat";

  private static final String JSON_KEY_SMS_IN = "smsIn";
  private static final String JSON_KEY_SMS_OUT = "smsOut";
  private static final String JSON_KEY_SMS_LABEL = "smsLabel";

  private static final String JSON_KEY_FOOTER_MODE = "footerMode";

  // the SQL where clause to use for selecting, updating,
  // or deleting the row for a given column
  // private static final String WHERE_SQL = DB_TABLE_ID + " = ? and "
  // + DB_ELEMENT_KEY + " = ?";

  // the columns to be selected when initializing ColumnProperties
  // private static final String[] INIT_COLUMNS = {
  // KeyValueStoreManager.TABLE_ID, DB_ELEMENT_KEY,
  // DB_ELEMENT_NAME, DB_ELEMENT_TYPE, DB_LIST_CHILD_ELEMENT_KEYS, DB_JOINS,
  // // DB_JOIN_TABLE_ID,
  // // DB_JOIN_ELEMENT_KEY,
  // DB_IS_PERSISTED,

  // DB_DISPLAY_VISIBLE,
  // DB_DISPLAY_NAME,
  // DB_DISPLAY_CHOICES_MAP,
  // DB_DISPLAY_FORMAT,
  // DB_SMS_IN,
  // DB_SMS_OUT,
  // DB_SMS_LABEL,
  // DB_FOOTER_MODE
  // };

  // Has moved to FooterMode.java.
  // public class FooterMode {
  // public static final int NONE = 0;
  // public static final int COUNT = 1;
  // public static final int MINIMUM = 2;
  // public static final int MAXIMUM = 3;
  // public static final int MEAN = 4;
  // public static final int SUM = 5;
  // private FooterMode() {}
  // }

  /***********************************
   * The fields that make up a ColumnProperties object.
   ***********************************/
  /*
   * The fields that belong only to the object, and are not related to the
   * actual column itself.
   */
  private final DbHelper dbh;
  // The type of key value store from which these properties were drawn.
  private final KeyValueStore.Type backingStore;
  /*
   * The fields that reside in ColumnDefinitions
   */
  private final String tableId;
  private final String elementKey;
  private String elementName;
  private ColumnType elementType;
  private List<String> listChildElementKeys;
  private JoinColumn joins;
  private boolean isPersisted;
  /*
   * The fields that reside in the key value store.
   */
  private boolean displayVisible;
  private String displayName;
  private ArrayList<String> displayChoicesMap;
  private String displayFormat;
  private boolean smsIn;
  private boolean smsOut;
  private String smsLabel;
  private FooterMode footerMode;

  private ColumnProperties(DbHelper dbh, String tableId, String elementKey, String elementName,
      ColumnType elementType, List<String> listChildElementKeys, JoinColumn joins,
      boolean isPersisted, boolean displayVisible, String displayName,
      ArrayList<String> displayChoicesMap, String displayFormat, boolean smsIn, boolean smsOut,
      String smsLabel, FooterMode footerMode, KeyValueStore.Type backingStore) {
    this.dbh = dbh;
    this.tableId = tableId;
    this.elementKey = elementKey;
    this.elementName = elementName;
    this.elementType = elementType;
    this.listChildElementKeys = listChildElementKeys;
    this.joins = joins;
    this.isPersisted = isPersisted;
    this.displayVisible = displayVisible;
    this.displayName = displayName;
    this.displayChoicesMap = displayChoicesMap;
    this.displayFormat = displayFormat;
    this.smsIn = smsIn;
    this.smsOut = smsOut;
    this.smsLabel = smsLabel;
    this.footerMode = footerMode;
    this.backingStore = backingStore;
  }

  /**
   * Return the ColumnProperties for the PERSISTED columns belonging to this
   * table. TODO: this should probably be modified in the future to return both
   * the persisted and non persisted columns. At the moment ODK Tables only
   * cares about the persisted columns, and with this message returning only
   * those columns it removes the need to deal with non-persisted columns at
   * this juncture.
   *
   * @param dbh
   * @param tableId
   * @return a map of elementKey to ColumnProperties for each persisted column.
   */
  static Map<String, ColumnProperties> getColumnPropertiesForTable(DbHelper dbh, String tableId,
      KeyValueStore.Type typeOfStore) {
    SQLiteDatabase db = null;
    try {
      db = dbh.getReadableDatabase();
      List<String> elementKeys = ColumnDefinitions.getPersistedElementKeysForTable(tableId, db);
      Map<String, ColumnProperties> elementKeyToColumnProperties = new HashMap<String, ColumnProperties>();
      for (int i = 0; i < elementKeys.size(); i++) {
        ColumnProperties cp = getColumnProperties(dbh, tableId, elementKeys.get(i), typeOfStore);
        elementKeyToColumnProperties.put(elementKeys.get(i), cp);
      }
      return elementKeyToColumnProperties;
    } finally {
      // // TODO: we need to resolve how we are going to prevent closing the
      // // db on callers. Removing this here, far far from ideal.
      // // if ( db != null ) {
      // // db.close();
      // // }
    }
  }

  /**
   * Retrieve the ColumnProperties for the column specified by the given table
   * id and the given dbElementKey.
   *
   * @param dbh
   * @param tableId
   * @param dbElementKey
   * @param typeOfStore
   *          the type of the backing store from which to source the mutable
   *          column properties
   * @return
   */
  private static ColumnProperties getColumnProperties(DbHelper dbh, String tableId,
      String elementKey, KeyValueStore.Type typeOfStore) {

    SQLiteDatabase db = dbh.getReadableDatabase();

    // Get the KVS values
    KeyValueStoreManager kvsm = KeyValueStoreManager.getKVSManager(dbh);
    KeyValueStore intendedKVS = kvsm.getStoreForTable(tableId, typeOfStore);
    Map<String, String> kvsMap = intendedKVS.getKeyValues(ColumnProperties.KVS_PARTITION,
        elementKey, db);

    // Get the ColumnDefinition entries
    Map<String, String> columnDefinitionsMap = ColumnDefinitions.getColumnDefinitionFields(tableId,
        elementKey, db);

    return constructPropertiesFromMap(dbh, tableId, elementKey, columnDefinitionsMap, kvsMap,
        typeOfStore);
  }

  /**
   * Construct a ColumnProperties from the given json serialization. NOTE: the
   * resulting ColumnProperties object has NOT been persisted to the database.
   * The caller is responsible for persisting it and/or adding it to the
   * TableProperties of the tableId.
   *
   * @param dbh
   * @param tableId
   * @param elementKey
   * @param columnDefinitions
   * @param kvsProps
   * @param backingStore
   * @return
   */
  private static ColumnProperties constructPropertiesFromMap(DbHelper dbh, String tableId,
      String elementKey, Map<String, String> columnDefinitions, Map<String, String> kvsProps,
      KeyValueStore.Type backingStore) {
    // First convert the non-string types to their appropriate types. This is
    // probably going to go away when the map becomes key->TypeValuePair.
    // KEY_SMS_IN
    String smsInStr = kvsProps.get(KEY_SMS_IN);
    boolean smsIn = SyncUtil.stringToBool(smsInStr);
    // KEY_SMS_OUT
    String smsOutStr = kvsProps.get(KEY_SMS_OUT);
    boolean smsOut = SyncUtil.stringToBool(smsOutStr);
    // KEY_DISPLAY_VISIBLE
    String displayVisibleStr = kvsProps.get(KEY_DISPLAY_VISIBLE);
    boolean displayVisible = SyncUtil.stringToBool(displayVisibleStr);
    // KEY_FOOTER_MODE
    String footerModeStr = kvsProps.get(KEY_FOOTER_MODE);
    // TODO don't forget that all of these value ofs for all these enums
    // should eventually be surrounded with try/catch to support versioning
    // when new values might come down from the server.
    FooterMode footerMode = (footerModeStr == null) ? DEFAULT_KEY_FOOTER_MODE : FooterMode
        .valueOf(footerModeStr);
    // DB_IS_PERSISTED
    String isPersistedStr = columnDefinitions.get(ColumnDefinitionsColumns.IS_PERSISTED);
    boolean isPersisted = SyncUtil.stringToBool(isPersistedStr);
    // DB_COLUMN_TYPE
    String columnTypeStr = columnDefinitions.get(ColumnDefinitionsColumns.ELEMENT_TYPE);
    ColumnType columnType = ColumnType.valueOf(columnTypeStr);

    // Now we need to reclaim the list values from their db entries.
    String parseValue = null;
    ArrayList<String> displayChoicesMap = null;
    ArrayList<String> listChildElementKeys = null;
    JoinColumn joins = null;
    try {
      if (kvsProps.get(KEY_DISPLAY_CHOICES_MAP) != null) {
        String displayChoicesMapValue = kvsProps.get(KEY_DISPLAY_CHOICES_MAP);
        parseValue = displayChoicesMapValue;
        displayChoicesMap = mapper.readValue(displayChoicesMapValue, ArrayList.class);
      }

      if (columnDefinitions.get(ColumnDefinitionsColumns.LIST_CHILD_ELEMENT_KEYS) != null) {
        String listChildElementKeysValue = columnDefinitions
            .get(ColumnDefinitionsColumns.LIST_CHILD_ELEMENT_KEYS);
        parseValue = listChildElementKeysValue;
        listChildElementKeys = mapper.readValue(listChildElementKeysValue, ArrayList.class);
      }
      if (columnDefinitions.get(ColumnDefinitionsColumns.JOINS) != null) {
        String joinsValue = columnDefinitions.get(ColumnDefinitionsColumns.JOINS);
        parseValue = joinsValue;
        joins = JoinColumn.fromSerialization(joinsValue);
      }
    } catch (JsonParseException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid db value: " + parseValue, e);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid db value: " + parseValue, e);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("invalid db value: " + parseValue, e);
    }
    return new ColumnProperties(dbh, tableId, elementKey,
        columnDefinitions.get(ColumnDefinitionsColumns.ELEMENT_NAME), columnType,
        listChildElementKeys, joins, isPersisted, displayVisible, kvsProps.get(KEY_DISPLAY_NAME),
        displayChoicesMap, kvsProps.get(KEY_DISPLAY_FORMAT), smsIn, smsOut,
        kvsProps.get(KEY_SMS_LABEL), footerMode, backingStore);
  }

  /**
   * NOTE: ONLY CALL THIS FROM TableProperties.addColumn() !!!!!!!
   *
   * Add a column to the datastore. elementKey and elementName should be made
   * via createDbElementKey and createDbElementName to avoid conflicts. A
   * possible idea would be to pass them display name.
   *
   * @param dbh
   * @param db
   * @param tableId
   * @param displayName
   * @param elementKey
   * @param elementName
   * @param columnType
   * @param listChildElementKeys
   * @param isPersisted
   * @param joins
   * @param displayVisible
   * @param typeOfStore
   * @return
   * @throws IOException
   * @throws JsonMappingException
   * @throws JsonGenerationException
   */
  void persistColumn(SQLiteDatabase db, String tableId) throws JsonGenerationException,
      JsonMappingException, IOException {
    // First prepare the entries for the key value store.
    List<OdkTablesKeyValueStoreEntry> values = new ArrayList<OdkTablesKeyValueStoreEntry>();
    values.add(createBooleanEntry(tableId, ColumnProperties.KVS_PARTITION, elementKey,
        KEY_DISPLAY_VISIBLE, displayVisible));
    values.add(createStringEntry(tableId, ColumnProperties.KVS_PARTITION, elementKey,
        KEY_DISPLAY_NAME, displayName));
    values.add(createStringEntry(tableId, ColumnProperties.KVS_PARTITION, elementKey,
        KEY_DISPLAY_CHOICES_MAP, mapper.writeValueAsString(displayChoicesMap)));
    values.add(createStringEntry(tableId, ColumnProperties.KVS_PARTITION, elementKey,
        KEY_DISPLAY_FORMAT, displayFormat));
    // TODO: both the SMS entries should become booleans?
    values.add(createBooleanEntry(tableId, ColumnProperties.KVS_PARTITION, elementKey, KEY_SMS_IN,
        smsIn));
    values.add(createBooleanEntry(tableId, ColumnProperties.KVS_PARTITION, elementKey, KEY_SMS_OUT,
        smsOut));
    values.add(createStringEntry(tableId, ColumnProperties.KVS_PARTITION, elementKey,
        KEY_SMS_LABEL, smsLabel));
    values.add(createStringEntry(tableId, ColumnProperties.KVS_PARTITION, elementKey,
        KEY_FOOTER_MODE, footerMode.name()));

    KeyValueStoreManager kvsm = KeyValueStoreManager.getKVSManager(dbh);
    KeyValueStore kvs = kvsm.getStoreForTable(tableId, backingStore);
    kvs.addEntriesToStore(db, values);

    ColumnDefinitions.assertColumnDefinition(db, tableId, elementKey, elementName, elementType,
        mapper.writeValueAsString(listChildElementKeys), isPersisted, joins);
  }

  public enum ColumnDefinitionChange {
    IDENTICAL, SAME_ELEMENT_TYPE, CHANGE_ELEMENT_TYPE, INCOMPATIBLE
  };

  /**
   * Determine if the changes between the current and supplied cp are
   * incompatible (cannot be reconciled), require altering the column type,
   * and/or require modifying the joins definition.
   *
   * @param cp
   * @return
   */
  ColumnDefinitionChange compareColumnDefinitions(ColumnProperties cp) {
    if (!this.getElementName().equals(cp.getElementName())) {
      return ColumnDefinitionChange.INCOMPATIBLE;
    }
    if (!this.getListChildElementKeys().equals(cp.getListChildElementKeys())) {
      return ColumnDefinitionChange.INCOMPATIBLE;
    }
    if (this.isPersisted() != cp.isPersisted()) {
      return ColumnDefinitionChange.INCOMPATIBLE;
    }
    if (this.getColumnType() != cp.getColumnType()) {
      return ColumnDefinitionChange.CHANGE_ELEMENT_TYPE;
    } else {
      // TODO: could save some reloading if we determine that
      // the two definitions are identical. For now, assume
      // they are not.
      return ColumnDefinitionChange.SAME_ELEMENT_TYPE;
    }
  }

  /**
   * NOTE: ONLY CALL THIS FROM TableProperties.addColumn() !!!!!!!
   *
   * Create a ColumnProperties object with the given values (assumed to be good).
   * Caller is responsible for persisting this to the database.
   *
   * @param dbh
   * @param tableId
   * @param displayName
   * @param elementKey
   * @param elementName
   * @param columnType
   * @param listChildElementKeys
   * @param isPersisted
   * @param joins
   * @param displayVisible
   * @param typeOfStore
   * @return
   */
  static ColumnProperties createNotPersisted(DbHelper dbh, String tableId,
      String displayName, String elementKey, String elementName, ColumnType columnType,
      List<String> listChildElementKeys, boolean isPersisted, JoinColumn joins,
      boolean displayVisible, KeyValueStore.Type typeOfStore) {

    ColumnProperties cp = new ColumnProperties(dbh, tableId, elementKey, elementName, columnType,
        listChildElementKeys, joins, isPersisted, displayVisible, displayName,
        DEFAULT_KEY_DISPLAY_CHOICES_MAP, DEFAULT_KEY_DISPLAY_FORMAT, DEFAULT_KEY_SMS_IN,
        DEFAULT_KEY_SMS_OUT, DEFAULT_KEY_SMS_LABEL, DEFAULT_KEY_FOOTER_MODE, typeOfStore);

    return cp;
  }

  /**
   * Deletes the column represented by this ColumnProperties by deleting it from
   * the ColumnDefinitions table as well as the given key value store.
   * <p>
   * Also clears all the column color rules for the column. TODO: should maybe
   * delete the column from ALL the column key value stores to avoid conflict
   * with ColumnDefinitions?
   *
   * @param db
   */
  void deleteColumn(SQLiteDatabase db) {
    ColumnDefinitions.deleteColumnDefinition(tableId, elementKey, db);
    KeyValueStoreManager kvsm = KeyValueStoreManager.getKVSManager(dbh);
    KeyValueStore kvs = kvsm.getStoreForTable(tableId, backingStore);
    kvs.clearEntries(ColumnProperties.KVS_PARTITION, elementKey, db);
    // this is to clear all the color rules. If we didn't do this, you could
    // have old color rules build up, and worse still, if you deleted this
    // column and then added a new column whose element key ended up being the
    // same, you would have rules suddenly applying to them.
    kvs.clearEntries(ColorRuleGroup.KVS_PARTITION_COLUMN, elementKey, db);
  }

  private static OdkTablesKeyValueStoreEntry createStringEntry(String tableId, String partition,
      String elementKey, String key, String value) {
    OdkTablesKeyValueStoreEntry entry = new OdkTablesKeyValueStoreEntry();
    entry.tableId = tableId;
    entry.partition = partition;
    entry.aspect = elementKey;
    entry.type = ColumnType.TEXT.name();
    entry.value = value;
    entry.key = key;
    return entry;
  }

  private static OdkTablesKeyValueStoreEntry createIntEntry(String tableId, String partition,
      String elementKey, String key, int value) {
    OdkTablesKeyValueStoreEntry entry = new OdkTablesKeyValueStoreEntry();
    entry.tableId = tableId;
    entry.partition = partition;
    entry.aspect = elementKey;
    entry.type = ColumnType.INTEGER.name();
    entry.value = Integer.toString(value);
    entry.key = key;
    return entry;
  }

  private static OdkTablesKeyValueStoreEntry createBooleanEntry(String tableId, String partition,
      String elementKey, String key, boolean value) {
    OdkTablesKeyValueStoreEntry entry = new OdkTablesKeyValueStoreEntry();
    entry.tableId = tableId;
    entry.partition = partition;
    entry.aspect = elementKey;
    entry.type = ColumnType.BOOLEAN.name();
    entry.value = Boolean.toString(value);
    entry.key = key;
    return entry;
  }

  /**
   * DB_ELEMENT_KEY, DB_ELEMENT_NAME, DB_ELEMENT_TYPE,
   * DB_LIST_CHILD_ELEMENT_KEYS, DB_JOIN_TABLE_ID, DB_JOIN_ELEMENT_KEY,
   * DB_IS_PERSISTED,
   *
   * DB_DISPLAY_VISIBLE, DB_DISPLAY_NAME, DB_DISPLAY_CHOICES_MAP,
   * DB_DISPLAY_FORMAT,
   *
   * DB_SMS_IN, DB_SMS_OUT, DB_SMS_LABEL,
   *
   * DB_FOOTER_MODE
   */

  public String getElementKey() {
    return elementKey;
  }

  public String getElementName() {
    return elementName;
  }

  /**
   * @return the column's type
   */
  public ColumnType getColumnType() {
    return elementType;
  }

  /**
   * Sets the column's type.
   *
   * @param columnType
   *          the new type
   */
  public void setColumnType(TableProperties tp, ColumnType columnType) {
    SQLiteDatabase db = dbh.getWritableDatabase();
    try {
      db.beginTransaction();
      setStringProperty(db, ColumnDefinitionsColumns.ELEMENT_TYPE, columnType.name());
      // TODO: we should run validation rules on the input, converting it to a
      // form that SQLite will properly convert into the new datatype.
      tp.reformTable(db);
      db.setTransactionSuccessful();
      db.endTransaction();
      this.elementType = columnType;
    } finally {
      tp.refreshColumns();
    }
  }

  public List<String> getListChildElementKeys() {
    return listChildElementKeys;
  }

  public void setListChildElementKeys(ArrayList<String> listChildElementKeys) {
    try {
      String strListChildElementKeys = null;
      if (listChildElementKeys != null && listChildElementKeys.size() > 0) {
        strListChildElementKeys = mapper.writeValueAsString(listChildElementKeys);
      }
      setStringProperty(ColumnDefinitionsColumns.LIST_CHILD_ELEMENT_KEYS, strListChildElementKeys);
      this.listChildElementKeys = listChildElementKeys;
    } catch (JsonGenerationException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setListChildElementKeys failed: "
          + listChildElementKeys.toString(), e);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setListChildElementKeys failed: "
          + listChildElementKeys.toString(), e);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setListChildElementKeys failed: "
          + listChildElementKeys.toString(), e);
    }
  }

  public boolean isPersisted() {
    return isPersisted;
  }

  public void setIsPersisted(boolean setting) {
    setBooleanProperty(ColumnDefinitionsColumns.IS_PERSISTED, setting);
    this.isPersisted = setting;
  }

  /**
   * @return whether or not this column is visible within Tables
   */
  public boolean getDisplayVisible() {
    return displayVisible;
  }

  /**
   * Sets whether or not this column is visible within Tables
   *
   * @param setting
   *          the new display visibility setting
   */
  public void setDisplayVisible(boolean setting) {
    setBooleanProperty(KEY_DISPLAY_VISIBLE, setting);
    this.displayVisible = setting;
  }

  /**
   * @return the column's display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Sets the column's display name.
   *
   * NOTE: The caller is responsible for confirming that the name will not be in
   * conflict with any other column display names in use within the table. Use
   * TableProperties.createDisplayName(proposedName) to do this.
   *
   * @param displayName
   *          the new display name
   * @return the
   */
  public void setDisplayName(String displayName) {
    setStringProperty(KEY_DISPLAY_NAME, displayName);
    this.displayName = displayName;
  }

  /**
   * @return the column's display format string or null if pass-through
   */
  public String getDisplayFormat() {
    return displayFormat;
  }

  /**
   * Sets the column's display format string.
   *
   * @param abbreviation
   *          the new abbreviation (or null for no abbreviation)
   */
  public void setDisplayFormat(String format) {
    setStringProperty(KEY_DISPLAY_FORMAT, format);
    this.displayFormat = format;
  }

  /**
   * @return the column's footer mode
   */
  public FooterMode getFooterMode() {
    return footerMode;
  }

  /**
   * Sets the column's footer mode.
   *
   * @param footerMode
   *          the new footer mode
   */
  public void setFooterMode(FooterMode footerMode) {
    setStringProperty(KEY_FOOTER_MODE, footerMode.name());
    this.footerMode = footerMode;
  }

  /**
   * @return the column's abbreviation (or null for no abbreviation)
   */
  public String getSmsLabel() {
    return smsLabel;
  }

  /**
   * Sets the column's abbreviation.
   *
   * @param abbreviation
   *          the new abbreviation (or null for no abbreviation)
   */
  public void setSmsLabel(String abbreviation) {
    setStringProperty(KEY_SMS_LABEL, abbreviation);
    this.smsLabel = abbreviation;
  }

  /**
   * @return the SMS-in setting
   */
  public boolean getSmsIn() {
    return smsIn;
  }

  /**
   * Sets the SMS-in setting.
   *
   * @param setting
   *          the new SMS-in setting
   */
  public void setSmsIn(boolean setting) {
    setBooleanProperty(KEY_SMS_IN, setting);
    this.smsIn = setting;
  }

  /**
   * @return the SMS-out setting
   */
  public boolean getSmsOut() {
    return smsOut;
  }

  /**
   * Sets the SMS-out setting.
   *
   * @param setting
   *          the new SMS-out setting
   */
  public void setSmsOut(boolean setting) {
    setBooleanProperty(KEY_SMS_OUT, setting);
    this.smsOut = setting;
  }

  /**
   * @return an array of the multiple-choice options
   */
  public ArrayList<String> getDisplayChoicesMap() {
    return displayChoicesMap;
  }

  /**
   * Sets the multiple-choice options.
   *
   * @param options
   *          the array of options
   * @throws IOException
   * @throws JsonMappingException
   * @throws JsonGenerationException
   */
  public void setDisplayChoicesMap(ArrayList<String> options) {
    try {
      String encoding = null;
      if (options != null && options.size() > 0) {
        encoding = mapper.writeValueAsString(options);
      }
      setStringProperty(KEY_DISPLAY_CHOICES_MAP, encoding);
      displayChoicesMap = options;
    } catch (JsonGenerationException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setDisplayChoicesMap failed: " + options.toString(), e);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setDisplayChoicesMap failed: " + options.toString(), e);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setDisplayChoicesMap failed: " + options.toString(), e);
    }
  }

  /**
   * @return the join definition
   */
  public JoinColumn getJoins() {
    return joins;
  }

  /**
   * Converts the JoinColumn to the json representation of the object using a
   * mapper and adds it to the database.
   * <p>
   * If there is a mapping exception of writing the JoinColumn to a String, it
   * does nothing, leaving the database untouched.
   *
   * @param joins
   */
  public void setJoins(JoinColumn joins) {
    try {
      String joinsStr = JoinColumn.toSerialization(joins);
      setStringProperty(ColumnDefinitionsColumns.JOINS, joinsStr);
      this.joins = joins;
    } catch (JsonGenerationException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setJoins failed: " + joins.toString(), e);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setJoins failed: " + joins.toString(), e);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("setJoins failed: " + joins.toString(), e);
    }
  }

  // Map<String, Object> toJson() {
  String toJson() throws JsonGenerationException, JsonMappingException, IOException {
    Map<String, Object> jo = new HashMap<String, Object>();
    jo.put(JSON_KEY_VERSION, 1);
    jo.put(JSON_KEY_TABLE_ID, tableId);
    jo.put(JSON_KEY_ELEMENT_KEY, elementKey);
    jo.put(JSON_KEY_ELEMENT_NAME, elementName);
    jo.put(JSON_KEY_JOINS, JoinColumn.toSerialization(joins));
    jo.put(JSON_KEY_ELEMENT_TYPE, elementType.name());
    jo.put(JSON_KEY_FOOTER_MODE, footerMode.name());
    jo.put(JSON_KEY_LIST_CHILD_ELEMENT_KEYS, listChildElementKeys);
    jo.put(JSON_KEY_IS_PERSISTED, isPersisted);
    jo.put(JSON_KEY_DISPLAY_VISIBLE, displayVisible);
    jo.put(JSON_KEY_DISPLAY_NAME, displayName);
    jo.put(JSON_KEY_DISPLAY_CHOICES_MAP, displayChoicesMap);
    jo.put(JSON_KEY_DISPLAY_FORMAT, displayFormat);
    jo.put(JSON_KEY_SMS_IN, smsIn);
    jo.put(JSON_KEY_SMS_OUT, smsOut);
    jo.put(JSON_KEY_SMS_LABEL, smsLabel);

    String toReturn = null;
    try {
      // I think this removes exceptions from not having getters/setters...
      toReturn = mapper.writeValueAsString(jo);
    } catch (JsonGenerationException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("toJson failed - tableId: " + tableId + " elementKey: "
          + elementKey, e);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("toJson failed - tableId: " + tableId + " elementKey: "
          + elementKey, e);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("toJson failed - tableId: " + tableId + " elementKey: "
          + elementKey, e);
    }
    return toReturn;
  }

  /**
   * Construct a ColumnProperties object from JSON. NOTE: Nothing is persisted
   * to the database. The caller is responsible for persisting the changes.
   *
   * @param dbh
   * @param json
   * @param typeOfStore
   * @return
   * @throws JsonParseException
   * @throws JsonMappingException
   * @throws IOException
   */
  public static ColumnProperties constructColumnPropertiesFromJson(DbHelper dbh, String json,
      KeyValueStore.Type typeOfStore) throws JsonParseException, JsonMappingException, IOException {
    Map<String, Object> jo;
    try {
      jo = mapper.readValue(json, Map.class);
    } catch (JsonParseException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("constructColumnPropertiesFromJson failed: " + json, e);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("constructColumnPropertiesFromJson failed: " + json, e);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("constructColumnPropertiesFromJson failed: " + json, e);
    }

    String joElType = (String) jo.get(JSON_KEY_ELEMENT_TYPE);
    ColumnType elementType = (joElType == null) ? ColumnType.NONE : ColumnType.valueOf(joElType);

    String joFootMode = (String) jo.get(JSON_KEY_FOOTER_MODE);
    FooterMode footerMode = (joFootMode == null) ? FooterMode.none : FooterMode.valueOf(joFootMode);

    JoinColumn joins = JoinColumn.fromSerialization((String) jo.get(JSON_KEY_JOINS));
    Object joListChildren = jo.get(JSON_KEY_LIST_CHILD_ELEMENT_KEYS);
    ArrayList<String> listChildren = (joListChildren == null) ? new ArrayList<String>()
        : (ArrayList<String>) joListChildren;
    Object joListChoices = jo.get(JSON_KEY_DISPLAY_CHOICES_MAP);
    ArrayList<String> listChoices = (joListChoices == null) ? new ArrayList<String>()
        : (ArrayList<String>) joListChoices;

    ColumnProperties cp = new ColumnProperties(dbh, (String) jo.get(JSON_KEY_TABLE_ID),
        (String) jo.get(JSON_KEY_ELEMENT_KEY), (String) jo.get(JSON_KEY_ELEMENT_NAME), elementType,
        listChildren, joins, (Boolean) jo.get(JSON_KEY_IS_PERSISTED),
        (Boolean) jo.get(JSON_KEY_DISPLAY_VISIBLE), (String) jo.get(JSON_KEY_DISPLAY_NAME),
        listChoices, (String) jo.get(JSON_KEY_DISPLAY_FORMAT), (Boolean) jo.get(JSON_KEY_SMS_IN),
        (Boolean) jo.get(JSON_KEY_SMS_OUT), (String) jo.get(JSON_KEY_SMS_LABEL), footerMode,
        typeOfStore);

    return cp;
  }

  private void setIntProperty(String property, int value) {
    SQLiteDatabase db = dbh.getWritableDatabase();
    try {
      setIntProperty(db, property, value);
    } finally {
      // TODO: fix the when to close problem
      // db.close();
    }
  }

  private void setIntProperty(SQLiteDatabase db, String property, int value) {
    if (ColumnDefinitions.columnNames.contains(property)) {
      ColumnDefinitions.setValue(tableId, elementKey, property, value, db);
    } else {
      // or a kvs property?
      KeyValueStoreManager kvsm = KeyValueStoreManager.getKVSManager(dbh);
      KeyValueStore kvs = kvsm.getStoreForTable(tableId, backingStore);
      kvs.insertOrUpdateKey(db, ColumnProperties.KVS_PARTITION, elementKey, property,
          ColumnType.INTEGER.name(), Integer.toString(value));
    }
    Log.d(TAG, "updated int property " + property + " to " + value + " for table " + tableId
        + ", column " + elementKey);
  }

  private void setStringProperty(String property, String value) {
    SQLiteDatabase db = dbh.getWritableDatabase();
    try {
      setStringProperty(db, property, value);
    } finally {
      // TODO: fix the when to close problem
      // db.close();
    }
  }

  private void setStringProperty(SQLiteDatabase db, String property, String value) {
    // is it a column definition property?
    if (ColumnDefinitions.columnNames.contains(property)) {
      ColumnDefinitions.setValue(tableId, elementKey, property, value, db);
    } else {
      // or a kvs property?
      KeyValueStoreManager kvsm = KeyValueStoreManager.getKVSManager(dbh);
      KeyValueStore kvs = kvsm.getStoreForTable(tableId, backingStore);
      kvs.insertOrUpdateKey(db, ColumnProperties.KVS_PARTITION, elementKey, property,
          ColumnType.TEXT.name(), value);
    }
    Log.d(TAG, "updated string property " + property + " to " + value + " for table " + tableId
        + ", column " + elementKey);
  }

  private void setBooleanProperty(String property, boolean value) {
    SQLiteDatabase db = dbh.getWritableDatabase();
    try {
      setBooleanProperty(db, property, value);
    } finally {
      // TODO: fix the when to close problem
      // db.close();
    }
  }

  private void setBooleanProperty(SQLiteDatabase db, String property, boolean value) {
    if (ColumnDefinitions.columnNames.contains(property)) {
      ColumnDefinitions.setValue(tableId, elementKey, property, value, db);
    } else {
      // or a kvs property?
      KeyValueStoreManager kvsm = KeyValueStoreManager.getKVSManager(dbh);
      KeyValueStore kvs = kvsm.getStoreForTable(tableId, backingStore);
      kvs.insertOrUpdateKey(db, ColumnProperties.KVS_PARTITION, elementKey, property,
          ColumnType.BOOLEAN.name(), Boolean.toString(value));
    }
    Log.d(TAG, "updated int property " + property + " to " + value + " for table " + tableId
        + ", column " + elementKey);
  }

}