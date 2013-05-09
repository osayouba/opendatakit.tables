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
package org.opendatakit.tables.sync.aggregate;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opendatakit.aggregate.odktables.api.client.AggregateRequestInterceptor;
import org.opendatakit.aggregate.odktables.entity.Column;
import org.opendatakit.aggregate.odktables.entity.OdkTablesKeyValueStoreEntry;
import org.opendatakit.aggregate.odktables.entity.Row;
import org.opendatakit.aggregate.odktables.entity.TableDefinition;
import org.opendatakit.aggregate.odktables.entity.TableProperties;
import org.opendatakit.aggregate.odktables.entity.api.PropertiesResource;
import org.opendatakit.aggregate.odktables.entity.api.RowResource;
import org.opendatakit.aggregate.odktables.entity.api.TableDefinitionResource;
import org.opendatakit.aggregate.odktables.entity.api.TableResource;
import org.opendatakit.aggregate.odktables.entity.api.TableType;
import org.opendatakit.aggregate.odktables.entity.serialization.JsonObjectHttpMessageConverter;
import org.opendatakit.aggregate.odktables.entity.serialization.SimpleXMLSerializerForAggregate;
import org.opendatakit.tables.data.ColumnType;
import org.opendatakit.tables.sync.IncomingModification;
import org.opendatakit.tables.sync.Modification;
import org.opendatakit.tables.sync.SyncRow;
import org.opendatakit.tables.sync.Synchronizer;
import org.opendatakit.tables.sync.exceptions.InvalidAuthTokenException;
import org.simpleframework.xml.Serializer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.SimpleXmlHttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Implementation of {@link Synchronizer} for ODK Aggregate.
 * 
 * @author the.dylan.price@gmail.com
 * 
 */
public class AggregateSynchronizer implements Synchronizer {

  private static final String TAG = AggregateSynchronizer.class.getSimpleName();
  private static final String TOKEN_INFO = "https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=";

  // TODO: how do we support new column types without breaking this map???
  public static final Map<ColumnType, Column.ColumnType> types = 
		  				new HashMap<ColumnType, Column.ColumnType>() {
    {
        put(ColumnType.NONE, Column.ColumnType.STRING);
        put(ColumnType.TEXT, Column.ColumnType.STRING);
        put(ColumnType.INTEGER, Column.ColumnType.INTEGER);
        put(ColumnType.NUMBER, Column.ColumnType.DECIMAL);
        put(ColumnType.DATE, Column.ColumnType.STRING);
        put(ColumnType.DATETIME, Column.ColumnType.STRING);
        put(ColumnType.TIME, Column.ColumnType.STRING);
        put(ColumnType.BOOLEAN, Column.ColumnType.BOOLEAN); // TODO: confirm this propagates OK?
        put(ColumnType.MIMEURI, Column.ColumnType.STRING); // TODO: need File + contentType entry in Aggregate (as JSON in Tables)
        put(ColumnType.MULTIPLE_CHOICES, Column.ColumnType.STRING); // TODO: should be extra-wide storage or split out in Aggregate???
        put(ColumnType.GEOPOINT, Column.ColumnType.STRING); // TODO: can we handle this generically?
      put(ColumnType.DATE_RANGE, Column.ColumnType.STRING); // not in Collect, Aggregate
      put(ColumnType.PHONE_NUMBER, Column.ColumnType.STRING); // not in Collect, Aggregate
      put(ColumnType.COLLECT_FORM, Column.ColumnType.STRING); // not in Collect, Aggregate

      // TODO: goes away -- becomes MULTIPLE_CHOICES + item element type
      put(ColumnType.MC_OPTIONS, Column.ColumnType.STRING); // select1/select - not in Collect, Aggregate
      
      // TODO: what is this for???
      put(ColumnType.TABLE_JOIN, Column.ColumnType.STRING);// not in Collect; needs to be in Aggregate
    }
    private static final long serialVersionUID = 1L;
  };

  private final RestTemplate rt;
  private final HttpHeaders requestHeaders;
  private final URI baseUri;
  private final Map<String, TableResource> resources;

  public AggregateSynchronizer(String aggregateUri, String accessToken)
      throws InvalidAuthTokenException {
    URI uri = URI.create(aggregateUri).normalize();
    uri = uri.resolve("/odktables/tables/").normalize();
    this.baseUri = uri;

    List<ClientHttpRequestInterceptor> interceptors = 
        new ArrayList<ClientHttpRequestInterceptor>();
    interceptors.add(new AggregateRequestInterceptor(accessToken));

    this.rt = new RestTemplate();
    this.rt.setInterceptors(interceptors);

    Serializer serializer = SimpleXMLSerializerForAggregate.getSerializer();
    List<HttpMessageConverter<?>> converters = 
        new ArrayList<HttpMessageConverter<?>>();
    converters.add(new JsonObjectHttpMessageConverter());
    converters.add(new SimpleXmlHttpMessageConverter(serializer));
    this.rt.setMessageConverters(converters);

    List<MediaType> acceptableMediaTypes = new ArrayList<MediaType>();
    acceptableMediaTypes.add(new MediaType("text", "xml"));

    this.requestHeaders = new HttpHeaders();
    this.requestHeaders.setAccept(acceptableMediaTypes);
    this.requestHeaders.setContentType(new MediaType("text", "xml"));

    this.resources = new HashMap<String, TableResource>();

    checkAccessToken(accessToken);
  }

  private void checkAccessToken(String accessToken) throws 
      InvalidAuthTokenException {
    try {
      rt.getForObject(TOKEN_INFO + accessToken, JsonObject.class);
    } catch (HttpClientErrorException e) {
      Log.e(TAG, "HttpClientErrorException in checkAccessToken");
      JsonParser parser = new JsonParser();
      JsonObject resp = parser.parse(e.getResponseBodyAsString())
          .getAsJsonObject();
      if (resp.has("error") && resp.get("error").getAsString()
          .equals("invalid_token")) {
        throw new InvalidAuthTokenException("Invalid auth token: " 
          + accessToken, e);
      }
    }
  }

  /**
   * Return a map of tableId to tableKey. 
   */
  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getTables() throws IOException {
    Map<String, String> tables = new HashMap<String, String>();

    List<TableResource> tableResources;
    try {
      tableResources = rt.getForObject(baseUri, List.class);
    } catch (ResourceAccessException e) {
      throw new IOException(e.getMessage());
    }

    for (TableResource tableResource : tableResources)
      tables.put(tableResource.getTableId(), tableResource.getTableKey());

    return tables;
  }

  /*
   * (non-Javadoc)
   * @see org.opendatakit.tables.sync.Synchronizer#createTable(j
   * ava.lang.String, java.util.List, java.lang.String, java.lang.String, 
   * org.opendatakit.aggregate.odktables.entity.api.TableType, 
   * java.lang.String)
   */
  @Override
  public String createTable(String tableId, List<Column> columns,
      String tableKey, String dbTableName, TableType type, 
      String tableIdAccessControls) throws IOException {
    
    // build request
    URI uri = baseUri.resolve(tableId);
    TableDefinition definition = 
        new TableDefinition(tableId, columns, tableKey, dbTableName, 
            type, tableIdAccessControls);
    HttpEntity<TableDefinition> requestEntity = 
        new HttpEntity<TableDefinition>(definition, requestHeaders);

    // create table
    ResponseEntity<TableResource> resourceEntity;
    try {
      // TODO: we also need to put up the key value store/properties.
      resourceEntity = rt.exchange(uri, HttpMethod.PUT, requestEntity, 
          TableResource.class);
    } catch (ResourceAccessException e) {
      Log.e(TAG, "ResourceAccessException in createTable");
      throw new IOException(e.getMessage());
    }
    TableResource resource = resourceEntity.getBody();

    // save resource
    this.resources.put(resource.getTableId(), resource);

    // return sync tag
    SyncTag syncTag = new SyncTag(resource.getDataEtag(), 
        resource.getPropertiesEtag());
    return syncTag.toString();
  }

  private TableResource getResource(String tableId) throws IOException {
    if (resources.containsKey(tableId)) {
      return resources.get(tableId);
    } else {
      return refreshResource(tableId);
    }
  }

  private TableResource refreshResource(String tableId) throws IOException {
    URI uri = baseUri.resolve(tableId);
    TableResource resource;
    try {
      resource = rt.getForObject(uri, TableResource.class);
    } catch (ResourceAccessException e) {
      throw new IOException(e.getMessage());
    }
    resources.put(resource.getTableId(), resource);
    return resource;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * yoonsung.odk.spreadsheet.sync.aggregate.Synchronizer#deleteTable(java.lang
   * .String)
   */
  @Override
  public void deleteTable(String tableId) {
    rt.delete(baseUri.resolve(tableId));
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * yoonsung.odk.spreadsheet.sync.aggregate.Synchronizer#getUpdates(java.lang
   * .String, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public IncomingModification getUpdates(String tableId, String currentSyncTag)
      throws IOException {
    IncomingModification modification = new IncomingModification();

    // get current and new sync tags
    TableResource resource = refreshResource(tableId);
    SyncTag currentTag;
    if (currentSyncTag != null)
      currentTag = SyncTag.valueOf(currentSyncTag);
    else
      currentTag = new SyncTag("", "");
    // This tag is ultimately returned. May8--make sure it works.
    SyncTag newTag = new SyncTag(resource.getDataEtag(), 
        resource.getPropertiesEtag());

    // stop now if there are no updates
    if (newTag.equals(currentTag)) {
      modification.setTableSyncTag(currentTag.toString());
      modification.setTablePropertiesChanged(false);
      return modification;
    }

    // get data updates
    if (!newTag.getDataEtag().equals(currentTag.getDataEtag())) {
      URI url;
      if (currentSyncTag == null) {
        url = URI.create(resource.getDataUri());
      } else {
        String diffUri = resource.getDiffUri();
        url = URI.create(diffUri + "?data_etag=" + 
            currentTag.getDataEtag()).normalize();
      }
      List<RowResource> rows;
      try {
        rows = rt.getForObject(url, List.class);
      } catch (ResourceAccessException e) {
        throw new IOException(e.getMessage());
      }

      List<SyncRow> syncRows = new ArrayList<SyncRow>();
      for (RowResource row : rows) {
        SyncRow syncRow = new SyncRow(row.getRowId(), row.getRowEtag(), 
            row.isDeleted(), row.getValues());
        syncRows.add(syncRow);
      }
      modification.setRows(syncRows);
    }

    // get properties updates.
    // To do this we first check to see if the properties Etag is up to date.
    // If it is, we can do nothing. If it is out of date, we have to:
    // 1) get a TableDefinitionResource to see if we need to update the table 
    // data structure of any of the columns. 
    // 2) get a PropertiesResource to get all the key value entries.
    if (!newTag.getPropertiesEtag().equals(currentTag.getPropertiesEtag())) {
      TableDefinitionResource definitionRes;
      PropertiesResource propertiesRes;
      try {
        propertiesRes = rt.getForObject(resource.getPropertiesUri(), 
            PropertiesResource.class);
        definitionRes = rt.getForObject(resource.getDefinitionUri(), 
            TableDefinitionResource.class);
      } catch (ResourceAccessException e) {
        throw new IOException(e.getMessage());
      }

      modification.setTablePropertiesChanged(true);
      modification.setTableProperties(propertiesRes);
      modification.setTableDefinitionResource(definitionRes);
    }

    modification.setTableSyncTag(newTag.toString());
    return modification;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * yoonsung.odk.spreadsheet.sync.aggregate.Synchronizer#insertRows(java.lang
   * .String, java.util.List)
   */
  @Override
  public Modification insertRows(String tableId, String currentSyncTag, List<SyncRow> rowsToInsert)
      throws IOException {
    List<Row> newRows = new ArrayList<Row>();
    for (SyncRow syncRow : rowsToInsert) {
      Row row = Row.forInsert(syncRow.getRowId(), syncRow.getValues());
      newRows.add(row);
    }
    return insertOrUpdateRows(tableId, currentSyncTag, newRows);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * yoonsung.odk.spreadsheet.sync.aggregate.Synchronizer#updateRows(java.lang
   * .String, java.util.List)
   */
  @Override
  public Modification updateRows(String tableId, String currentSyncTag, List<SyncRow> rowsToUpdate)
      throws IOException {
    List<Row> changedRows = new ArrayList<Row>();
    for (SyncRow syncRow : rowsToUpdate) {
      Row row = Row.forUpdate(syncRow.getRowId(), syncRow.getSyncTag(), syncRow.getValues());
      changedRows.add(row);
    }
    return insertOrUpdateRows(tableId, currentSyncTag, changedRows);
  }

  private Modification insertOrUpdateRows(String tableId, String currentSyncTag, List<Row> rows)
      throws IOException {
    TableResource resource = getResource(tableId);
    SyncTag syncTag = SyncTag.valueOf(currentSyncTag);
    Map<String, String> rowTags = new HashMap<String, String>();

    if (!rows.isEmpty()) {
      for (Row row : rows) {
        URI url = URI.create(resource.getDataUri() + "/" + row.getRowId()).normalize();
        HttpEntity<Row> requestEntity = new HttpEntity<Row>(row, requestHeaders);
        ResponseEntity<RowResource> insertedEntity;
        try {
          insertedEntity = rt.exchange(url, HttpMethod.PUT, requestEntity, RowResource.class);
        } catch (ResourceAccessException e) {
          throw new IOException(e.getMessage());
        }
        RowResource inserted = insertedEntity.getBody();
        rowTags.put(inserted.getRowId(), inserted.getRowEtag());
        syncTag.incrementDataEtag();
      }
    }

    Modification modification = new Modification();
    modification.setSyncTags(rowTags);
    modification.setTableSyncTag(syncTag.toString());

    return modification;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * yoonsung.odk.spreadsheet.sync.aggregate.Synchronizer#deleteRows(java.lang
   * .String, java.util.List)
   */
  @Override
  public String deleteRows(String tableId, String currentSyncTag, List<String> rowIds)
      throws IOException {
    TableResource resource = getResource(tableId);
    SyncTag syncTag = SyncTag.valueOf(currentSyncTag);

    if (!rowIds.isEmpty()) {
      for (String rowId : rowIds) {
        URI url = URI.create(resource.getDataUri() + "/" + rowId).normalize();
        try {
          rt.delete(url);
        } catch (ResourceAccessException e) {
          throw new IOException(e.getMessage());
        }
        syncTag.incrementDataEtag();
      }
    }

    return syncTag.toString();
  }

  /*
   * (non-Javadoc)
   * @see org.opendatakit.tables.sync.Synchronizer#setTableProperties(
   * java.lang.String, java.lang.String, java.lang.String, java.util.List)
   */
  @Override
  public String setTableProperties(String tableId, String currentSyncTag,
      String tableKey, List<OdkTablesKeyValueStoreEntry> kvsEntries) 
          throws IOException {
    TableResource resource = getResource(tableId);
    SyncTag currentTag = SyncTag.valueOf(currentSyncTag);

    // put new properties
    TableProperties properties = 
        new TableProperties(currentTag.getPropertiesEtag(), tableKey,
            kvsEntries);
    HttpEntity<TableProperties> entity = 
        new HttpEntity<TableProperties>(properties, requestHeaders);
    ResponseEntity<PropertiesResource> updatedEntity;
    try {
      updatedEntity = rt.exchange(resource.getPropertiesUri(), HttpMethod.PUT, 
          entity, PropertiesResource.class);
    } catch (ResourceAccessException e) {
      throw new IOException(e.getMessage());
    }
    PropertiesResource propsResource = updatedEntity.getBody();

    SyncTag newTag = new SyncTag(currentTag.getDataEtag(), 
        propsResource.getPropertiesEtag());
    return newTag.toString();
  }
}
