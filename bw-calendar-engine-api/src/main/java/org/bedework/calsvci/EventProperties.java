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
package org.bedework.calsvci;

import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.util.misc.response.GetEntitiesResponse;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;

import java.io.Serializable;
import java.util.Collection;

/** Interface which handles manipulation of BwEventProperty subclasses which are
 * treated in the same manner, these being Category, Location and Contact.
 *
 * <p>Each has a single field which together with the owner makes a unique
 * key and all operations on those classes are the same.
 *
 * @author Mike Douglass   douglm - rpi.edu
 *
 * @param <T> type of property, Location, Sponsor etc.
 */
public interface EventProperties <T extends BwEventProperty> extends Serializable {
  /** Initialise the object
   *
   * @param className     Class of entity
   * @param adminCanEditAllPublic   True if administrators can edit all public entities
   */
  void init(String className,
            boolean adminCanEditAllPublic);

  /** Return all public entities.
   *
   * <p>Returns an empty collection for none.
   *
   * <p>The returned objects will not be persistent objects.
   *
   * @return Collection     of objects
   * @throws CalFacadeException on fatal error
   */
  Collection<T> getPublic() throws CalFacadeException;

  /** Return all entities satisfying the conditions and to which the current
   * user has edit access.
   *
   * <p>Returns an empty collection for none.
   *
   * <p>The returned objects may not be persistent objects but the result of a
   * report query.
   *
   * @return Collection     of objects
   * @throws CalFacadeException on fatal error
   */
  Collection<T> getEditable() throws CalFacadeException;

  /** Return a cached version of the entity given the uid - if the user has access
   *
   * <p>This entity will not be a live version - it is a detached copy which may
   * be out of date. The cache entries will be refreshed fairly frequently.
   *
   * @param uid       String uid
   * @return Response containing status and BwEventProperty object
   *                   if it exists.
   * @throws RuntimeException on fatal error
   */
  GetEntityResponse<T> getByUid(String uid);

  /** Return a non-persistent version of the entity given the href - if the user has access
   *
   * <p>This entity will not be a live version - it is a detached copy which may
   * be out of date. The cache entries will be refreshed fairly frequently.
   *
   * @param href       String href
   * @return BwEventProperty object representing the entity in question
   *                     null if it doesn't exist.
   * @throws CalFacadeException on fatal error
   */
  T get(String href) throws CalFacadeException;

  /** Return all current user entities.
   *
   * <p>Returns an empty collection for none.
   *
   * <p>The returned objects may not be persistent objects but the result of a
   * report query.
   *
   * @return Collection     of objects
   * @throws CalFacadeException on fatal error
   */
  Collection<T> get() throws CalFacadeException;

  /** Return an entity given the uid if the user has access
   *
   * @param uid       String uid
   * @return BwEventProperty object representing the entity in question
   *                     null if it doesn't exist.
   * @throws CalFacadeException on fatal error
   */
  T getPersistent(String uid) throws CalFacadeException;

  /** Return cached versions of the entity given the uids - if the user has access
   *
   * <p>These entities will not be live versions - but detached copies which may
   * be out of date. The cache entries will be refreshed fairly frequently.
   *
   * @param uids       Collection of String uids
   * @return response containing status and non-null but possibly
   *                  empty collection of BwEventProperty objects.
   *                  Never returns notFound
   */
  GetEntitiesResponse<T> getByUids(Collection<String> uids);

  /** Return one or more entities matching the given BwString and
   * owned by the current principal.
   *
   * <p>All event properties have string values which are used as the external
   * representation in icalendar files. The combination of field and owner
   * should be unique. The field value may change over time while the
   * uid does not.
   *
   * @param val          BwString value
   * @return Response with status and matching BwEventProperty object
   */
  GetEntityResponse<T> findPersistent(final BwString val);

  /** Return an entity matching the given BwString to which the
   * user has access.
   *
   * <p>All event properties have string values which are used as the external
   * representation in icalendar files. The field should be unique
   * for the owner. The field value may change over time while the
   * uid does not.
   *
   * @param val          BwString value
   * @return matching BwEventProperty object
   * @throws CalFacadeException on fatal error
   */
  T find(final BwString val) throws CalFacadeException;

  /** Return all entities matching the given filter expression to which the
   * user has access.
   *
   * @param fexpr          filter expression - will be restriced to type
   * @return matching BwEventProperty objects and status
   */
  GetEntitiesResponse<T> find(String fexpr,
                              int from,
                              int size);

  /** Add an entity to the database. The id will be set in the parameter
   * object.
   *
   * @param val   BwEventProperty object to be added
   * @return boolean true for added, false for already exists
   */
  Response add(T val);

  /** Update an entity in the database.
   *
   * @param val   BwEventProperty object to be updated
   * @throws CalFacadeException on fatal error
   */
  void update(T val) throws CalFacadeException;

  /** Delete an entity
   *
   * @param val      BwEventProperty object to be deleted
   * @return int     0 if it was deleted.
   *                 1 if it didn't exist
   *                 2 if in use
   * @throws CalFacadeException on fatal error
   */
  int delete(T val) throws CalFacadeException;

  /** Return references to the entity
   *
   * @param val an entity
   * @return a collection of references.
   * @throws CalFacadeException on faltal error
   */
  Collection<EventPropertiesReference> getRefs(T val) throws CalFacadeException;

  /** Returned to show if an entity was added. entity is set to retrieved entity
   *
   * @param <T>
   */
  class EnsureEntityExistsResult<T> extends GetEntityResponse<T> {
    /** Was added */
    public boolean added;
  }

  /** Ensure an entity exists. If it already does returns the object.
   * If not creates the entity.
   *
   * @param val     T object. If this object has the id set,
   *                we assume the check was made previously.
   * @param ownerHref   String principal href, null for current user
   * @return EnsureEntityExistsResult  with entity set to actual object.
   */
  EnsureEntityExistsResult<T> ensureExists(final T val,
                                           final String ownerHref);

  /** Reindex current users entities
   *
   * @param indexer to use for this operation
   * @return number of entities reindexed
   * @throws CalFacadeException on fatal error
   */
  int reindex(BwIndexer indexer) throws CalFacadeException;
}

