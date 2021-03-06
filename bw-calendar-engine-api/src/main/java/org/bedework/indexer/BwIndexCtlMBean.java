/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.indexer;

import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.indexing.IndexStatsResponse;
import org.bedework.util.jmx.ConfBaseMBean;
import org.bedework.util.jmx.MBeanInfo;

import java.util.List;

/**
 * @author douglm
 *
 */
public interface BwIndexCtlMBean extends ConfBaseMBean, IndexProperties {
  /**
   * @return number of messages processed
   */
  long getMessageCount();

  /**
   * @return count processed
   */
  long getCollectionsUpdated();

  /**
   * @return count processed
   */
  long getCollectionsDeleted();

  /**
   * @return count processed
   */
  long getEntitiesUpdated();

  /**
   * @return count processed
   */
  long getEntitiesDeleted();

  /** Get the current status of the reindexing process
   *
   * @return messages as a list
   */
  List<String> rebuildStatus();

  /** Crawl the db data and create indexes - listener should have been stopped.
   *
   * @return message
   */
  String rebuildIndex();

  /** Crawl the db data and reindex the resources - this is done in place.
   *
   * @return message
   */
  String rebuildEntityIndex(String docType);

  /** Creates a new index for use by reindex
   * 
   * @return index name.
   */
  String newIndexes();

  /** Reindex the current docType index into a new index. Used for schema
   * changes etc.
   *
   * @return result.
   */
  String reindex(String docType);

  /** Move the production index alias to the given index
   *
   * @param indexName name of index to be aliased
   * @return result.
   */
  String setProdAlias(final String indexName);

  /** Move the production index aliases to the latest index
   *
   * @return result.
   */
  String makeAllProd();

  IndexStatsResponse indexStats(String indexName);

  /**
   * @return list of indexes maintained by indexer.
   */
  String listIndexes();

  /**
   * @return list of purged indexes.
   */
  String purgeIndexes();

  /** Start the indexer
   *
   */
  void start();

  /** Stop the indexer
   *
   */
  void stop();

  /** Lifecycle
   *
   * @return true if started
   */
  boolean isStarted();

  /** (Re)load the configuration
   *
   * @return status
   */
  @MBeanInfo("(Re)load the configuration")
  String loadConfig();
}
