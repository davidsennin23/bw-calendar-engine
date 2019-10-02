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
package org.bedework.calsvc;

import org.bedework.access.Access;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeSet;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfFactory;
import org.bedework.caldav.util.notifications.admin.AdminNoteParsers;
import org.bedework.caldav.util.notifications.eventreg.EventregParsers;
import org.bedework.caldav.util.notifications.suggest.SuggestParsers;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.CacheStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeConstraintViolationException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.mail.MailerIntf;
import org.bedework.calfacade.responses.GetEntityResponse;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.UserAuth;
import org.bedework.calfacade.svc.wrappers.BwCalSuiteWrapper;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.calsvc.scheduling.Scheduling;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvci.AdminI;
import org.bedework.calsvci.CalSuitesI;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.Categories;
import org.bedework.calsvci.Contacts;
import org.bedework.calsvci.DumpIntf;
import org.bedework.calsvci.EventsI;
import org.bedework.calsvci.FiltersI;
import org.bedework.calsvci.Locations;
import org.bedework.calsvci.NotificationsI;
import org.bedework.calsvci.PreferencesI;
import org.bedework.calsvci.ResourcesI;
import org.bedework.calsvci.RestoreIntf;
import org.bedework.calsvci.SchedulingI;
import org.bedework.calsvci.SharingI;
import org.bedework.calsvci.SynchI;
import org.bedework.calsvci.SynchReport;
import org.bedework.calsvci.SynchReportItem;
import org.bedework.calsvci.SysparsI;
import org.bedework.calsvci.TimeZonesStoreI;
import org.bedework.calsvci.UsersI;
import org.bedework.calsvci.ViewsI;
import org.bedework.icalendar.IcalCallback;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.jmx.MBeanUtil;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.security.PwEncryptionIntf;
import org.bedework.util.security.keys.GenKeysMBean;
import org.bedework.util.timezones.Timezones;

import java.sql.Blob;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** This is an implementation of the service level interface to the calendar
 * suite.
 *
 * @author Mike Douglass       douglm rpi.edu
 */
public class CalSvc extends CalSvcI implements Logged, Calintf.FilterParserFetcher {
  //private String systemName;

  private CalSvcIPars pars;

  private static Configurations configs;

  static {
    try {
      configs = new CalSvcFactoryDefault().getSystemConfig();
      new SuggestParsers(); // force load
      new EventregParsers(); // force load
      new AdminNoteParsers(); // force load
    } catch (final Throwable t) {
      t.printStackTrace();
    }
  }

  private boolean open;

  //private boolean superUser;

  /** True if this is a session to create a new account. Do not try to create one
   */
  private boolean creating;

  private boolean authenticated;

  /* Information about current user */
  private PrincipalInfo principalInfo;

  /* The account that we are representing
   */
//  private BwUser currentUser;

  /* The account we logged in as - for user access equals currentUser, for admin
   * access currentUser is the group we are managing.
   */
//  private BwUser currentAuthUser;

  /* If we're doing admin this is the authorised user entry
   */
  BwAuthUser adminUser;

  /* ....................... Handlers ..................................... */

  //private MailerIntf mailer;

  private PreferencesI prefsHandler;

  private AdminI adminHandler;

  private EventsI eventsHandler;

  private FiltersI filtersHandler;

  private CalendarsI calendarsHandler;

  private SysparsI sysparsHandler;

  private CalSuitesI calSuitesHandler;

  private NotificationsI notificationsHandler;

  private ResourcesI resourcesHandler;

  private SchedulingIntf sched;

  private SharingI sharingHandler;

  private SynchI synch;

  private UsersI usersHandler;

  private ViewsI viewsHandler;

  private Categories categoriesHandler;

  private Locations locationsHandler;

  private Contacts contactsHandler;

  private final Collection<CalSvcDb> handlers = new ArrayList<>();

  /* ....................... ... ..................................... */

  /** Core calendar interface
   */
  private transient Calintf cali;

  private transient PwEncryptionIntf pwEncrypt;

  /** handles timezone info.
   */
  private Timezones timezones;
  private TimeZonesStoreI tzstore;

  /* null if timezones not initialised */
  private static String tzserverUri = null;

  /** The user authorisation object
   */
  private UserAuth userAuth;

  private transient UserAuth.CallBack uacb;

  private transient Directories.CallBack gcb;

  /** The user groups object.
   */
  private Directories userGroups;

  /** The admin groups object.
   */
  private Directories adminGroups;

  private IcalCallback icalcb;

  @Override
  public void init(final CalSvcIPars parsParam) throws CalFacadeException {
    init(parsParam, false);
  }

  private void init(final CalSvcIPars parsParam,
                    final boolean creating) throws CalFacadeException {
    pars = (CalSvcIPars)parsParam.clone();

    this.creating = creating;

    final long start = System.currentTimeMillis();

    fixUsers();

    try {
      if (configs == null) {
        // Try again - failed at static init?
        configs = new CalSvcFactoryDefault().getSystemConfig();
      }

      open();
      beginTransaction();

      if (userGroups != null) {
        userGroups.init(getGroupsCallBack(),
                        configs);
      }

      if (adminGroups != null) {
        adminGroups.init(getGroupsCallBack(),
                         configs);
      }

      final SystemProperties sp = getSystemProperties();

      if (tzserverUri == null) {
        tzserverUri = sp.getTzServeruri();

        if (tzserverUri == null) {
          throw new CalFacadeException("No timezones server URI defined");
        }

        Timezones.initTimezones(tzserverUri);

        Timezones.setSystemDefaultTzid(sp.getTzid());
      }

      /* Some checks on parameter validity
       */
      //        BwUser =

      tzstore = new TimeZonesStoreImpl(this);

      /* Nominate our timezone registry */
      System.setProperty("net.fortuna.ical4j.timezone.registry",
      "org.bedework.icalendar.TimeZoneRegistryFactoryImpl");

      if (!creating) {
        final String tzid = getPrefsHandler().get().getDefaultTzid();

        if (tzid != null) {
          Timezones.setThreadDefaultTzid(tzid);
        }

        //        if (pars.getCaldav() && !pars.isGuest()) {
        if (!pars.isGuest()) {
          /* Ensure scheduling resources exist */
//          getCal().getSpecialCalendar(getPrincipal(), BwCalendar.calTypeInbox,
  //                                    true, PrivilegeDefs.privAny);

    //      getCal().getSpecialCalendar(getPrincipal(), BwCalendar.calTypeOutbox,
      //                                true, PrivilegeDefs.privAny);
        }
      }

      if ((pars.getPublicAdmin() || pars.getAllowSuperUser()) &&
          (pars.getAuthUser() != null)) {
        ((SvciPrincipalInfo)principalInfo).setSuperUser(
             getSysparsHandler().isRootUser(principalInfo.getAuthPrincipal()));
      }

      postNotification(
        SysEvent.makePrincipalEvent(SysEvent.SysCode.USER_SVCINIT,
                                                      getPrincipal(),
                                                      System.currentTimeMillis() - start));
    } catch (final CalFacadeException cfe) {
      rollbackTransaction();
      cfe.printStackTrace();
      throw cfe;
    } catch (final Throwable t) {
      rollbackTransaction();
      t.printStackTrace();
      throw new CalFacadeException(t);
    } finally {
      try {
        endTransaction();
      } catch (final Throwable ignored) {}
      try {
        close();
      } catch (final Throwable ignored) {}
    }
  }

  @Override
  public BasicSystemProperties getBasicSystemProperties() {
    return configs.getBasicSystemProperties();
  }

  @Override
  public AuthProperties getAuthProperties() {
    return configs.getAuthProperties(authenticated);
  }

  @Override
  public AuthProperties getAuthProperties(final boolean auth) {
    return configs.getAuthProperties(auth);
  }

  @Override
  public SystemProperties getSystemProperties() {
    return configs.getSystemProperties();
  }

  @Override
  public NotificationProperties getNotificationProperties() {
    return configs.getNotificationProps();
  }

  @Override
  public IndexProperties getIndexProperties() {
    return configs.getIndexProperties();
  }

  @Override
  public void setCalSuite(final String name) throws CalFacadeException {
    final BwCalSuiteWrapper cs = getCalSuitesHandler().get(name);

    if (cs == null) {
      error("******************************************************");
      error("Unable to fetch calendar suite " + name);
      error("Is the database correctly initialised?");
      error("******************************************************");
      throw new CalFacadeException(CalFacadeException.unknownCalsuite,
          name);
    }

    getCalSuitesHandler().set(cs);

    /* This is wrong. The calsuite doesn't always represent the group
       It may be a sub-group.
    final BwPrincipal user = getUsersHandler().getPrincipal(cs.getGroup().getOwnerHref());
    user.setGroups(getDirectories().getAllGroups(user));

    if (!user.equals(principalInfo.getPrincipal())) {
      ((SvciPrincipalInfo)principalInfo).setPrincipal(user);
    }
    */
  }

  @Override
  public PrincipalInfo getPrincipalInfo() {
    return principalInfo;
  }

  @Override
  public boolean getSuperUser() {
    return principalInfo.getSuperUser();
  }

  @Override
  public byte[] getPublicKey(final String domain,
                             final String service) throws CalFacadeException {
    try {
      return getEncrypter().getPublicKey();
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public BwStats getStats() throws CalFacadeException {
    final BwStats stats = getCal().getStats();

    if (timezones != null) {
      final CacheStats cs = stats.getDateCacheStats();

      cs.setHits(timezones.getDateCacheHits());
      cs.setMisses(timezones.getDateCacheMisses());
      cs.setCached(timezones.getDatesCached());
    }

    stats.setAccessStats(Access.getStatistics());

    return stats;
  }

  @Override
  public void setDbStatsEnabled(final boolean enable) throws CalFacadeException {
    //if (!pars.getPublicAdmin()) {
    //  throw new CalFacadeAccessException();
    //}

    getCal().setDbStatsEnabled(enable);
  }

  @Override
  public boolean getDbStatsEnabled() throws CalFacadeException {
    return getCal().getDbStatsEnabled();
  }

  @Override
  public void dumpDbStats() throws CalFacadeException {
    //if (!pars.getPublicAdmin()) {
    //  throw new CalFacadeAccessException();
    //}

    trace(getStats().toString());
    getCal().dumpDbStats();
  }

  @Override
  public Collection<StatsEntry> getDbStats() throws CalFacadeException {
    //if (!pars.getPublicAdmin()) {
    //  throw new CalFacadeAccessException();
    //}

    return getCal().getDbStats();
  }

  @Override
  public void logStats() throws CalFacadeException {
    info(getStats().toString());
  }

  @Override
  public IfInfo getIfInfo() throws CalFacadeException {
    return getCal().getIfInfo();
  }

  @Override
  public List<IfInfo> getActiveIfInfos() throws CalFacadeException {
    final List<IfInfo> ifs = new ArrayList<>();

    for (final Calintf ci: getCal().active()) {
      ifs.add(ci.getIfInfo());
    }

    return ifs;
  }

  @Override
  public void kill(final IfInfo ifInfo) {
    // We could probably use some sort of kill listener to clean up after this

    try {
      for (final Calintf ci: getCal().active()) {
        final IfInfo calIfInfo = ci.getIfInfo();
        
        if (calIfInfo.getId().equals(ifInfo.getId())) {
          warn("Stopping interface with id " + ifInfo.getId());

          ci.kill();
          break;
        }
      }
    } catch (final Throwable t) {
      error(t);
    }
  }

  @Override
  public void setState(final String val) throws CalFacadeException {
    getCal().setState(val);
  }

  @Override
  public void postNotification(final SysEventBase ev) throws CalFacadeException {
    getCal().postNotification(ev);
  }

  @Override
  public void flushAll() throws CalFacadeException {
    getCal().flush();
  }

  @Override
  public void open() throws CalFacadeException {
    //TimeZoneRegistryImpl.setThreadCb(getIcalCallback());

    if (open) {
      return;
    }

    open = true;
    getCal().open(this,
                  pars.getLogId(),
                  configs,
                  pars.getWebMode(),
                  pars.getForRestore(),
                  pars.getIndexRebuild(),
                  pars.getPublicAdmin(),
                  pars.getPublicSubmission(),
                  pars.getSessionsless(),
                  pars.getDontKill());

    for (final CalSvcDb handler: handlers) {
      handler.open();
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isRolledback() throws CalFacadeException {
    return open && getCal().isRolledback();

  }

  @Override
  public void close() throws CalFacadeException {
    open = false;
    getCal().close();

    for (final CalSvcDb handler: handlers) {
      handler.close();
    }
  }

  @Override
  public void beginTransaction() throws CalFacadeException {
    getCal().beginTransaction();
  }

  @Override
  public void endTransaction() throws CalFacadeException {
    getCal().endTransaction();
  }

  @Override
  public void rollbackTransaction() throws CalFacadeException {
    getCal().rollbackTransaction();
  }

  @Override
  public Timestamp getCurrentTimestamp() throws CalFacadeException {
    return getCal().getCurrentTimestamp();
  }

  public Blob getBlob(final byte[] val) throws CalFacadeException {
    return getCal().getBlob(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#reAttach(org.bedework.calfacade.base.BwDbentity)
   */
  @Override
  public void reAttach(final BwDbentity val) throws CalFacadeException {
    getCal().reAttach(val);
  }

  @Override
  public BwUnversionedDbentity merge(final BwUnversionedDbentity val) throws CalFacadeException {
    if (val instanceof CalendarWrapper) {
      final CalendarWrapper w = (CalendarWrapper)val;
      w.putEntity((BwCalendar)getCal().merge(w.fetchEntity()));
      return w;
    }

    return getCal().merge(val);
  }

  @Override
  public IcalCallback getIcalCallback() {
    if (icalcb == null) {
      icalcb = new IcalCallbackcb(null);
    }

    return icalcb;
  }

  @Override
  public IcalCallback getIcalCallback(final Boolean timezonesByReference) {
    if (icalcb == null) {
      icalcb = new IcalCallbackcb(timezonesByReference);
    }

    return icalcb;
  }

  /* ====================================================================
   *                   Factory methods
   * ==================================================================== */

  @Override
  public DumpIntf getDumpHandler() throws CalFacadeException {
    return new DumpImpl(this);
  }

  @Override
  public RestoreIntf getRestoreHandler() throws CalFacadeException {
    return new RestoreImpl(this);
  }

  class SvcSimpleFilterParser extends SimpleFilterParser {
    final FlushMap<String, BwCalendar> cols = new FlushMap<>(20, 60 * 1000,
                                                             100);

    @Override
    public BwCalendar getCollection(final String path)
            throws CalFacadeException {
      BwCalendar col = cols.get(path);
      if (col != null) {
        return col;
      }

      col = getCalendarsHandler().get(path);
      cols.put(path, col);

      return col;
    }

    @Override
    public BwCalendar resolveAlias(final BwCalendar val,
                                   final boolean resolveSubAlias)
            throws CalFacadeException {
      return getCalendarsHandler().resolveAlias(val, resolveSubAlias, false);
    }

    @Override
    public Collection<BwCalendar> getChildren(final BwCalendar col)
            throws CalFacadeException {
      final String path = col.getPath();
      BwCalendar cachedCol = cols.get(path);

      if ((cachedCol != null) && (cachedCol.getChildren() != null)) {
        return col.getChildren();
      }

      Collection<BwCalendar> children = getCalendarsHandler().getChildren(col);

      if (cachedCol == null) {
        cachedCol = col;
      }

      cachedCol.setChildren(children);

      cols.put(path, cachedCol);

      return children;
    }

    @Override
    public BwCategory getCategoryByName(final String name) throws CalFacadeException {
      return getCategoriesHandler().find(new BwString(null, name));
    }

    @Override
    public BwCategory getCategoryByUid(final String uid) throws CalFacadeException {
      return getCategoriesHandler().getByUid(uid);
    }

    @Override
    public BwView getView(final String path)
            throws CalFacadeException {
      return getViewsHandler().find(path);
    }

    @Override
    public Collection<BwCalendar> decomposeVirtualPath(final String vpath)
            throws CalFacadeException {
      return getCalendarsHandler().decomposeVirtualPath(vpath);
    }

    @Override
    public SimpleFilterParser getParser() throws CalFacadeException {
      return new SvcSimpleFilterParser();
    }
  }

  @Override
  public SimpleFilterParser getFilterParser() {
    return new SvcSimpleFilterParser();
  }

  @Override
  public SysparsI getSysparsHandler() throws CalFacadeException {
    if (sysparsHandler == null) {
      sysparsHandler = new Syspars(this);
      handlers.add((CalSvcDb)sysparsHandler);
    }

    return sysparsHandler;
  }

  @Override
  public MailerIntf getMailer() throws CalFacadeException {
    /*
    if (mailer != null) {
      return mailer;
    }*/

    try {
      final MailerIntf mailer =
              (MailerIntf)CalFacadeUtil.getObject(getSystemProperties().getMailerClass(),
                                                  MailerIntf.class);
      mailer.init(configs.getMailConfigProperties());

      return mailer;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getPrefsHandler()
   */
  @Override
  public PreferencesI getPrefsHandler() throws CalFacadeException {
    if (prefsHandler == null) {
      prefsHandler = new Preferences(this);
      handlers.add((CalSvcDb)prefsHandler);
    }

    return prefsHandler;
  }

  @Override
  public AdminI getAdminHandler() throws CalFacadeException {
    if (!isPublicAdmin()) {
      throw new CalFacadeAccessException();
    }

    if (adminHandler == null) {
      adminHandler = new Admin(this);
      handlers.add((CalSvcDb)adminHandler);
    }

    return adminHandler;
  }

  @Override
  public EventsI getEventsHandler() {
    if (eventsHandler == null) {
      eventsHandler = new Events(this);
      handlers.add((CalSvcDb)eventsHandler);
    }

    return eventsHandler;
  }

  @Override
  public FiltersI getFiltersHandler() {
    if (filtersHandler == null) {
      filtersHandler = new Filters(this);
      handlers.add((CalSvcDb)filtersHandler);
    }

    return filtersHandler;
  }

  @Override
  public CalendarsI getCalendarsHandler() throws CalFacadeException {
    if (calendarsHandler == null) {
      calendarsHandler = new Calendars(this);
      handlers.add((CalSvcDb)calendarsHandler);
    }

    return calendarsHandler;
  }

  @Override
  public CalSuitesI getCalSuitesHandler() throws CalFacadeException {
    if (calSuitesHandler == null) {
      calSuitesHandler = new CalSuites(this);
      handlers.add((CalSvcDb)calSuitesHandler);
    }

    return calSuitesHandler;
  }

  public BwIndexer getIndexer(final String docType) {
    try {
      return getCal().getIndexer(docType);
    } catch (final CalFacadeException cfe) {
      throw new RuntimeException(cfe);
    }
  }

  @Override
  public BwIndexer getIndexer(final boolean publick,
                              final String docType) {
    try {
      return getCal().getIndexer(publick, docType);
    } catch (final CalFacadeException cfe) {
      throw new RuntimeException(cfe);
    }
  }

  @Override
  public BwIndexer getIndexer(final BwOwnedDbentity entity) {
    try {
      return getCal().getIndexer(entity);
    } catch (final CalFacadeException cfe) {
      throw new RuntimeException(cfe);
    }
  }

  @Override
  public BwIndexer getIndexer(final String principal,
                              final String docType) {
    try {
      final String prHref;

      if (principal == null) {
        prHref = getPrincipal().getPrincipalRef();
      } else {
        prHref = principal;
      }

      return getCal().getIndexer(prHref, docType);
    } catch (final CalFacadeException cfe) {
      throw new RuntimeException(cfe);
    }
  }

  @Override
  public BwIndexer getIndexerForReindex(final String principal,
                                        final String docType,
                                        final String indexName) {
    try {
      final String prHref;

      if (principal == null) {
        prHref = getPrincipal().getPrincipalRef();
      } else {
        prHref = principal;
      }

      return getCal().getIndexerForReindex(prHref, docType, indexName);
    } catch (final CalFacadeException cfe) {
      throw new RuntimeException(cfe);
    }
  }

  @Override
  public NotificationsI getNotificationsHandler() throws CalFacadeException {
    if (notificationsHandler == null) {
      notificationsHandler = new Notifications(this);
      handlers.add((CalSvcDb)notificationsHandler);
    }

    return notificationsHandler;
  }

  @Override
  public ResourcesI getResourcesHandler() throws CalFacadeException {
    if (resourcesHandler == null) {
      resourcesHandler = new ResourcesImpl(this);
      handlers.add((CalSvcDb)resourcesHandler);
    }

    return resourcesHandler;
  }

  @Override
  public SchedulingI getScheduler() throws CalFacadeException {
    if (sched == null) {
      sched = new Scheduling(this);
      handlers.add((CalSvcDb)sched);
    }

    return sched;
  }

  @Override
  public SharingI getSharingHandler() throws CalFacadeException {
    if (sharingHandler == null) {
      sharingHandler = new Sharing(this);
      handlers.add((CalSvcDb)sharingHandler);
    }

    return sharingHandler;
  }

  @Override
  public SynchI getSynch() throws CalFacadeException {
    if (synch == null) {
      try {
        synch = new Synch(this, configs.getSynchConfig());
        handlers.add((CalSvcDb)synch);
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    return synch;
  }

  @Override
  public UsersI getUsersHandler()  {
    if (usersHandler == null) {
      usersHandler = new Users(this);
      handlers.add((CalSvcDb)usersHandler);
    }

    return usersHandler;
  }

  @Override
  public ViewsI getViewsHandler() throws CalFacadeException {
    if (viewsHandler == null) {
      viewsHandler = new Views(this);
      handlers.add((CalSvcDb)viewsHandler);
    }

    return viewsHandler;
  }

  @Override
  public Directories getDirectories() throws CalFacadeException {
    if (isPublicAdmin()) {
      return getAdminDirectories();
    }

    return getUserDirectories();
  }

  @Override
  public Directories getUserDirectories() throws CalFacadeException {
    if (userGroups != null) {
      return userGroups;
    }

    try {
      userGroups = (Directories)CalFacadeUtil.getObject(getSystemProperties().getUsergroupsClass(), Directories.class);
      userGroups.init(getGroupsCallBack(),
                      configs);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    return userGroups;
  }

  @Override
  public Directories getAdminDirectories() throws CalFacadeException {
    if (adminGroups != null) {
      return adminGroups;
    }

    try {
      adminGroups = (Directories)CalFacadeUtil.getObject(getSystemProperties().getAdmingroupsClass(), Directories.class);
      adminGroups.init(getGroupsCallBack(),
                       configs);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    return adminGroups;
  }

  @Override
  public Categories getCategoriesHandler() {
    if (categoriesHandler == null) {
      categoriesHandler = new CategoriesImpl(this);
      categoriesHandler.init(pars.getAdminCanEditAllPublicCategories());
      handlers.add((CalSvcDb)categoriesHandler);
    }

    return categoriesHandler;
  }

  @Override
  public Locations getLocationsHandler() {
    if (locationsHandler == null) {
      locationsHandler = new LocationsImpl(this);
      locationsHandler.init(pars.getAdminCanEditAllPublicLocations());
      handlers.add((CalSvcDb)locationsHandler);
    }

    return locationsHandler;
  }

  @Override
  public Contacts getContactsHandler() {
    if (contactsHandler == null) {
      contactsHandler = new ContactsImpl(this);
      contactsHandler.init(pars.getAdminCanEditAllPublicContacts());
      handlers.add((CalSvcDb)contactsHandler);
    }

    return contactsHandler;
  }

  /* ====================================================================
   *                       dump/restore methods
   *
   * These are used to handle the needs of dump/restore. Perhaps we
   * can eliminate the need at some point...
   * ==================================================================== */

  @Override
  public <T>  Iterator<T> getObjectIterator(Class<T> cl) {
    try {
      return getCal().getObjectIterator(cl);
    } catch (final CalFacadeException cfe) {
      throw new RuntimeException(cfe);
    }
  }

  @Override
  public <T> Iterator<T> getPrincipalObjectIterator(final Class<T> cl) {
    try {
      return getCal().getPrincipalObjectIterator(cl);
    } catch (final CalFacadeException cfe) {
      throw new RuntimeException(cfe);
    }
  }

  @Override
  public <T> Iterator<T> getPublicObjectIterator(Class<T> cl) {
    try {
      return getCal().getPublicObjectIterator(cl);
    } catch (final CalFacadeException cfe) {
      throw new RuntimeException(cfe);
    }
  }

  /* ====================================================================
   *                   Users and accounts
   * ==================================================================== */

  @Override
  public BwPrincipal getPrincipal() {
    return principalInfo.getPrincipal();
  }

  @Override
  public BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    return getCal().getPrincipal(href);
  }

  @Override
  public UserAuth getUserAuth() throws CalFacadeException {
    if (userAuth != null) {
      return userAuth;
    }

    userAuth = (UserAuth)CalFacadeUtil.getObject(getSystemProperties().getUserauthClass(),
                                                 UserAuth.class);

    userAuth.initialise(getUserAuthCallBack());

    return userAuth;
  }

  @Override
  public long getUserMaxEntitySize() throws CalFacadeException {
    long max = getPrefsHandler().get().getMaxEntitySize();

    if (max != 0) {
      return max;
    }

    return getAuthProperties().getMaxUserEntitySize();
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) throws CalFacadeException {
    return getCal().getPreferences(principalHref);
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */

  @Override
  public void removeFromAllPrefs(final BwShareableDbentity val) throws CalFacadeException {
    getCal().removeFromAllPrefs(val);
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup findGroup(final String account,
                           final boolean admin) throws CalFacadeException {
    return getCal().findGroup(account, admin);
  }

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  @Override
  public void changeAccess(BwShareableDbentity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) throws CalFacadeException {
    if (ent instanceof BwCalSuiteWrapper) {
      ent = ((BwCalSuiteWrapper)ent).fetchEntity();
    }
    getCal().changeAccess(ent, aces, replaceAll);

    if (ent instanceof BwCalendar) {
      final BwCalendar col = (BwCalendar)ent;

      if (col.getCalType() == BwCalendar.calTypeInbox) {
        // Same access as inbox
        final BwCalendar pendingInbox = 
                getCalendarsHandler().getSpecial(BwCalendar.calTypePendingInbox,
                                                 true);
        if (pendingInbox == null) {
          warn("Unable to update pending inbox access");
        } else {
          getCal().changeAccess(pendingInbox, aces, replaceAll);
        }
      }

      ((Preferences)getPrefsHandler()).updateAdminPrefs(false,
                                         col,
                                         null,
                                         null,
                                         null);
    } else if (ent instanceof BwEventProperty) {
      ((Preferences)getPrefsHandler()).updateAdminPrefs(false,
                                                        (BwEventProperty)ent);
    }
  }

  @Override
  public void defaultAccess(BwShareableDbentity ent,
                            final AceWho who) throws CalFacadeException {
    if (ent instanceof BwCalSuiteWrapper) {
      ent = ((BwCalSuiteWrapper)ent).fetchEntity();
    }
    getCal().defaultAccess(ent, who);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#checkAccess(org.bedework.calfacade.base.BwShareableDbentity, int, boolean)
   */
  @Override
  public CurrentAccess checkAccess(final BwShareableDbentity ent, final int desiredAccess,
                                   final boolean returnResult) throws CalFacadeException {
    return getCal().checkAccess(ent, desiredAccess, returnResult);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getSynchReport(java.lang.String, java.lang.String, int, boolean)
   */
  @Override
  public SynchReport getSynchReport(final String path,
                                    final String token,
                                    final int limit,
                                    final boolean recurse) throws CalFacadeException {
    final BwCalendar col = getCalendarsHandler().get(path);
    if (col == null) {
      return null;
    }

    Set<SynchReportItem> items = new TreeSet<>();
    String resToken = getSynchItems(col, path, token, items, recurse);
    final SynchReport res = new SynchReport(items, resToken);

    if ((limit > 0) && (res.size() >= limit)) {
      if (res.size() == limit) {
        return res;
      }

      items = new TreeSet<>();
      resToken = "";

      for (final SynchReportItem item: res.getItems()) {
        if (item.getToken().compareTo(resToken) > 0) {
          resToken = item.getToken();
        }

        items.add(item);

        if (items.size() == limit) {
          break;
        }
      }
    }

    if (resToken.length() == 0) {
      resToken = Util.icalUTCTimestamp() + "-0000";
    }

    return new SynchReport(items, resToken);
  }

  private boolean canSync(final BwCalendar col) {
    //if (col.getCalType() == BwCalendar.calTypeAlias) {
    //  return false;
    //}

    return col.getCalType() != BwCalendar.calTypeExtSub;
  }

  /* ====================================================================
   *                   Timezones
   * ==================================================================== */

  @Override
  public UpdateFromTimeZonesInfo updateFromTimeZones(final String colHref,
                                                     final int limit,
                                                     final boolean checkOnly,
                                                     final UpdateFromTimeZonesInfo info
                                                     ) throws CalFacadeException {
    return tzstore.updateFromTimeZones(colHref, limit, checkOnly, info);
  }

  /* ====================================================================
   *                   Get back end interface
   * ==================================================================== */

  /* This will get a calintf based on the supplied collection object.
   */
  Calintf getCal(final BwCalendar cal) throws CalFacadeException {
    return getCal();
  }

  /* We need to synchronize this code to prevent stale update exceptions.
   * db locking might be better - this could still fail in a clustered
   * environment for example.
   */
  private static volatile Object synchlock = new Object();

  private static final Map<String, BwPrincipal> authUsers = new HashMap<>();

  private static final Map<String, BwPrincipal> unauthUsers = new HashMap<>();

  /* Currently this gets a local calintf only. Later we need to use a par to
   * get calintf from a table.
   */
  Calintf getCal() throws CalFacadeException {
    if (cali != null) {
      return cali;
    }

    final long start = System.currentTimeMillis();

    try {
      final long beforeGetIntf = System.currentTimeMillis() - start;

      String authenticatedUser = pars.getAuthUser();

      authenticated = pars.getForRestore()
              || authenticatedUser != null;

      if (!authenticated || pars.getReadonly()) {
        cali = CalintfFactory
                .getIntf(CalintfFactory.indexerOnlyClass);
      } else {
        cali = CalintfFactory.getIntf(CalintfFactory.hibernateClass);
      }

      final long afterGetIntf = System.currentTimeMillis() - start;

      cali.open(this,
                pars.getLogId(),
                configs,
                pars.getWebMode(),
                pars.getForRestore(),
                pars.getIndexRebuild(),
                pars.getPublicAdmin(),
                pars.getPublicSubmission(),
                pars.getSessionsless(),
                pars.getDontKill()); // Just for the user interactions

      postNotification(SysEvent.makeTimedEvent(
              "Login: about to obtain calintf",
              beforeGetIntf));
      postNotification(
              SysEvent.makeTimedEvent("Login: calintf obtained",
                                      afterGetIntf));
      postNotification(
              SysEvent.makeTimedEvent("Login: intf opened",
                                      System.currentTimeMillis() - start));

      cali.beginTransaction();

      postNotification(
              SysEvent.makeTimedEvent("Login: transaction started",
                                      System.currentTimeMillis() - start));

      String runAsUser = pars.getUser();

      if (pars.getCalSuite() != null) {
        final BwCalSuite cs = cali.getCalSuite(pars.getCalSuite());

        if (cs == null) {
          error("******************************************************");
          error("Unable to fetch calendar suite " + pars
                  .getCalSuite());
          error("Is the database correctly initialised?");
          error("******************************************************");
          throw new CalFacadeException(
                  CalFacadeException.unknownCalsuite,
                  pars.getCalSuite());
        }

        getCalSuitesHandler().set(new BwCalSuiteWrapper(cs));
        /* For administrative use we use the account of the admin group the user
         * is a direct member of
         *
         * For public clients we use the calendar suite owning group.
         */
        if (!pars.getPublicAdmin()) {
          runAsUser = cs.getGroup().getOwnerHref();
        }
      }

      postNotification(
              SysEvent.makeTimedEvent("Login: before get dirs",
                                      System.currentTimeMillis() - start));

      final Directories dir = getDirectories();

      /* Get ourselves a user object */
      if (authenticatedUser != null) {
        final String sv = authenticatedUser;

        if (dir.isPrincipal(authenticatedUser)) {
          authenticatedUser = dir
                  .accountFromPrincipal(authenticatedUser);
        }

        if (authenticatedUser == null) {
          error("Failed with Authenticated user " + sv);
          return null;
        }

        if (authenticatedUser.endsWith("/")) {
          getLogger().warn("Authenticated user " + authenticatedUser +
                                   " ends with \"/\"");
        }
      }

      postNotification(
              SysEvent.makeTimedEvent("Login: before user fetch",
                                      System.currentTimeMillis() - start));

      //synchronized (synchlock) {
      final Users users = (Users)getUsersHandler();

      if (runAsUser == null) {
        runAsUser = authenticatedUser;
      }

      BwPrincipal currentPrincipal;
      final BwPrincipal authPrincipal;
      PrivilegeSet maxAllowedPrivs = null;
      boolean subscriptionsOnly = getSystemProperties()
              .getUserSubscriptionsOnly();
      boolean userMapHit = false;
      boolean addingUser = false;
      boolean addingRunAsUser = false;

      if (pars.getForRestore()) {
        currentPrincipal = dir.caladdrToPrincipal(pars.getAuthUser());
        authPrincipal = currentPrincipal;
        subscriptionsOnly = false;
      } else if (authenticatedUser == null) {
        // Unauthenticated use

        currentPrincipal = unauthUsers.get(runAsUser);

        if (currentPrincipal == null) {
          currentPrincipal = users.getUser(runAsUser);
        } else {
          userMapHit = true;
        }
        if (currentPrincipal == null) {
          // XXX Should we set this one up?
          currentPrincipal = BwPrincipal.makeUserPrincipal();
        }

        currentPrincipal.setUnauthenticated(true);

        if (!userMapHit) {
          unauthUsers.put(runAsUser, currentPrincipal);
        }
        authPrincipal = currentPrincipal;
        maxAllowedPrivs = PrivilegeSet.readOnlyPrivileges;
      } else {
        currentPrincipal = unauthUsers.get(authenticatedUser);

        if (currentPrincipal == null) {
          currentPrincipal = users.getUser(authenticatedUser);
        } else {
          userMapHit = true;
        }

        if (currentPrincipal == null) {
          /* Add the user to the database. Presumably this is first logon
             */
          getLogger().debug("Add new user " + authenticatedUser);

          /*
            currentPrincipal = addUser(authenticatedUser);
            if (currentPrincipal == null) {
              error("Failed to find user after adding: " + authenticatedUser);
            }
            */
          currentPrincipal = getFakeUser(authenticatedUser);
          addingUser = true;
        }

        authPrincipal = currentPrincipal;

        if (authenticatedUser.equals(runAsUser)) {
          getLogger()
                  .debug("Authenticated user " + authenticatedUser +
                                 " logged on");
        } else {
          currentPrincipal = unauthUsers.get(runAsUser);

          if (currentPrincipal == null) {
            currentPrincipal = users.getUser(runAsUser);
          } else {
            userMapHit = true;
          }

          if (currentPrincipal == null) {
            //              throw new CalFacadeException("User " + runAsUser + " does not exist.");
            /* Add the user to the database. Presumably this is first logon
               */
            getLogger().debug("Add new run-as-user " + runAsUser);

            //currentPrincipal = addUser(runAsUser);
            currentPrincipal = getFakeUser(runAsUser);
            addingRunAsUser = true;
          }

          getLogger()
                  .debug("Authenticated user " + authenticatedUser +
                                 " logged on - running as " + runAsUser);
        }

        if (!userMapHit && (currentPrincipal != null)) {
          currentPrincipal
                  .setGroups(dir.getAllGroups(currentPrincipal));
          authUsers.put(currentPrincipal.getAccount(),
                        currentPrincipal);
        }

        postNotification(
                SysEvent.makeTimedEvent("Login: after get Groups",
                                        System.currentTimeMillis() - start));

        if (pars.getService()) {
          subscriptionsOnly = false;
        } else {
          final BwPrincipalInfo bwpi = dir
                  .getDirInfo(currentPrincipal);
          currentPrincipal.setPrincipalInfo(bwpi);

          if (pars.getPublicAdmin() || (bwpi != null && bwpi
                  .getHasFullAccess())) {
            subscriptionsOnly = false;
          }

          postNotification(
                  SysEvent.makeTimedEvent("Login: got Dirinfo",
                                          System.currentTimeMillis() - start));
        }
      }

      principalInfo = new SvciPrincipalInfo(this,
                                            currentPrincipal,
                                            authPrincipal,
                                            maxAllowedPrivs,
                                            subscriptionsOnly);

      cali.initPinfo(principalInfo);

      if (addingUser) {
        // Do the real work of setting up user
        addUser(authenticatedUser);
      }

      if (addingRunAsUser) {
        // Do the real work of setting up user
        addUser(runAsUser);
      }

      if (!currentPrincipal.getUnauthenticated()) {
        if (pars.getService()) {
          postNotification(
                  SysEvent.makePrincipalEvent(
                          SysEvent.SysCode.SERVICE_USER_LOGIN,
                          currentPrincipal,
                          System.currentTimeMillis() - start));
        } else if (!creating) {
          users.logon(currentPrincipal);

          postNotification(
                  SysEvent.makePrincipalEvent(
                          SysEvent.SysCode.USER_LOGIN,
                          currentPrincipal,
                          System.currentTimeMillis() - start));
        }

        if (debug()) {
          final Collection<BwGroup> groups =
                  currentPrincipal.getGroups();
          if (!Util.isEmpty(groups)) {
            for (final BwGroup group: groups) {
              debug("Group: " + group.getAccount());
            }
          }
        }
      } else {
        // If we have a runAsUser it's a public client. Pretend we authenticated
// WHY?          currentPrincipal.setUnauthenticated(runAsUser == null);
      }

      if (pars.getPublicAdmin() || pars.isGuest()) {
        if (debug()) {
          debug("PublicAdmin: " + pars.getPublicAdmin() + " user: "
                        + runAsUser);
        }

        /* We may be running as a different user. The preferences we want to see
           * are those of the user we are running as - i.e. the 'run.as' user
           * not those of the authenticated user.
           * /

          BwCalSuiteWrapper suite = getCalSuitesHandler().get();
          BwPrincipal user;

          if (suite != null) {
            // Use this user
            user = users.getPrincipal(suite.getGroup().getOwnerHref());
          } else if (runAsUser == null) {
            // Unauthenticated CalDAV for example?
            user = currentPrincipal;
          } else {
            // No calendar suite set up

            // XXX This is messy
            if (runAsUser.startsWith("/")) {
              user = users.getPrincipal(runAsUser);
            } else {
              user = users.getUser(runAsUser);
            }
          }

          if (!user.equals(principalInfo.getPrincipal())) {
            user.setGroups(getDirectories().getAllGroups(user));
            user.setPrincipalInfo(getDirectories().getDirInfo(user));
            ((SvciPrincipalInfo)principalInfo).setPrincipal(user);
          }

           */
      }

      return cali;
      //}
    } catch (final CalFacadeException cfe) {
      error(cfe);
      throw cfe;
    } catch (final Throwable t) {
      error(t);
      throw new CalFacadeException(t);
    } finally {
      if (cali != null) {
        cali.endTransaction();
        cali.close();
        //cali.flushAll();
      }
    }
  }

  void initPrincipal(final BwPrincipal p) throws CalFacadeException {
    getCal().addNewCalendars(p);
  }

  /** Switch to the given principal to allow us to update their stuff - for
   * example - send a notification.
   *
   * @param principal a principal object
   */
  void pushPrincipal(final BwPrincipal principal) throws CalFacadeException {
    BwPrincipal pr = getUsersHandler().getUser(principal.getPrincipalRef());

    if (pr == null) {
      pr = addUser(principal.getPrincipalRef());
    }

    ((SvciPrincipalInfo)principalInfo).pushPrincipal(pr);
    getCal().principalChanged();
  }

  /** Switch back to the previous principal.
   *
   * @throws CalFacadeException on fatal error
   */
  void popPrincipal() throws CalFacadeException {
    ((SvciPrincipalInfo)principalInfo).popPrincipal();
    getCal().principalChanged();
  }

  BwPrincipal getFakeUser(final String account) throws CalFacadeException {
    final Users users = (Users)getUsersHandler();

    /* Run this in a separate transaction to ensure we don't fail if the user
     * gets created by a concurrent process.
     */

    // Get a fake user
    return users.initUserObject(account);
  }

  /* Create the user. Get a new CalSvc object for that purpose.
   *
   */
  BwPrincipal addUser(final String val) throws CalFacadeException {
    final Users users = (Users)getUsersHandler();

    /* Run this in a separate transaction to ensure we don't fail if the user
     * gets created by a concurrent process.
     */

    //if (creating) {
    //  // Get a fake user
    //  return users.initUserObject(val);
    //}

    getCal().flush(); // In case we need to replace the session

    try {
      users.createUser(val);
    } catch (final CalFacadeException cfe) {
      if (cfe instanceof CalFacadeConstraintViolationException) {
        // We'll assume it was created by another process.
        warn("ConstraintViolationException trying to create " + val);

        // Does this session still work?
      } else {
        rollbackTransaction();
        if (debug()) {
          cfe.printStackTrace();
        }
        throw cfe;
      }
    } catch (final Throwable t) {
      rollbackTransaction();
      if (debug()) {
        t.printStackTrace();
      }
      throw new CalFacadeException(t);
    }

    final BwPrincipal principal = users.getUser(val);

    if (principal == null) {
      return null;
    }

    final String caladdr =
            getDirectories().userToCaladdr(principal.getPrincipalRef());
    if (caladdr != null) {
      final List<String> emails = Collections.singletonList(caladdr.substring("mailto:".length()));
      final Notifications notify = (Notifications)getNotificationsHandler();
      notify.subscribe(principal, emails);
    }
    return principal;
  }

  private UserAuthCallBack getUserAuthCallBack() {
    if (uacb == null) {
      uacb = new UserAuthCallBack(this);
    }

    return (UserAuthCallBack)uacb;
  }

  private GroupsCallBack getGroupsCallBack() {
    if (gcb == null) {
      gcb = new GroupsCallBack(this);
    }

    return (GroupsCallBack)gcb;
  }

  private class IcalCallbackcb implements IcalCallback {
    private int strictness = conformanceRelaxed;

    private final Boolean timezonesByReference;

    IcalCallbackcb(final Boolean timezonesByReference) {
      this.timezonesByReference = timezonesByReference;
    }

    @Override
    public void setStrictness(final int val) throws CalFacadeException {
      strictness = val;
    }

    @Override
    public int getStrictness() throws CalFacadeException {
      return strictness;
    }

    @Override
    public BwPrincipal getPrincipal() throws CalFacadeException {
      return CalSvc.this.getPrincipal();
    }

    @Override
    public BwPrincipal getOwner() throws CalFacadeException {
      if (isPublicAdmin()) {
        return getUsersHandler().getPublicUser();
      }

      return CalSvc.this.getPrincipal();
    }

    @Override
    public String getCaladdr(final String val) throws CalFacadeException {
      return getDirectories().userToCaladdr(val);
    }

    @Override
    public BwCategory findCategory(final BwString val) throws CalFacadeException {
      return getCategoriesHandler().findPersistent(val);
    }

    @Override
    public void addCategory(final BwCategory val) throws CalFacadeException {
      getCategoriesHandler().add(val);
    }

    @Override
    public BwContact getContact(final String uid) throws CalFacadeException {
      return getContactsHandler().getByUid(uid);
    }

    @Override
    public BwContact findContact(final BwString val) throws CalFacadeException {
      return getContactsHandler().findPersistent(val);
    }

    @Override
    public void addContact(final BwContact val) throws CalFacadeException {
      getContactsHandler().add(val);
    }

    @Override
    public BwLocation getLocation(final String uid) throws CalFacadeException {
      return getLocationsHandler().getByUid(uid);
    }

    @Override
    public BwLocation getLocation(final BwString address) throws CalFacadeException {
      return getLocationsHandler().findPersistent(address);
    }

    @Override
    public GetEntityResponse<BwLocation> fetchLocationByKey(
            final String name,
            final String val) {
      return getLocationsHandler().fetchLocationByKey(name, val);
    }

    @Override
    public BwLocation findLocation(final BwString address) throws CalFacadeException {
      final BwLocation loc = BwLocation.makeLocation();
      loc.setAddress(address);

      return getLocationsHandler().ensureExists(loc,
                                                getOwner().getPrincipalRef()).entity;
    }

    @Override
    public GetEntityResponse<BwLocation> fetchLocationByCombined(
            final String val, final boolean persisted) {
      return getLocationsHandler().fetchLocationByCombined(val, persisted);
    }

    @Override
    public void addLocation(final BwLocation val) throws CalFacadeException {
      getLocationsHandler().add(val);
    }

    @Override
    public Collection getEvent(final String colPath,
                               final String guid)
            throws CalFacadeException {
      return getEventsHandler().getByUid(colPath, guid,
                                         null,
                                         RecurringRetrievalMode.overrides);
    }

    @Override
    public boolean getTimezonesByReference() throws CalFacadeException {
      if (timezonesByReference != null) {
        return timezonesByReference;
      }

      return getSystemProperties().getTimezonesByReference();
    }
  }

  /* Remove trailing "/" from user principals.
   */
  private void fixUsers() {
    String auser = pars.getAuthUser();
    while ((auser != null) && (auser.endsWith("/"))) {
      auser = auser.substring(0, auser.length() - 1);
    }

    pars.setAuthUser(auser);
  }

  private String getSynchItems(final BwCalendar col,
                               final String vpath,
                               final String token,
                               final Set<SynchReportItem> items,
                               final boolean recurse) throws CalFacadeException {
    final Events eventsH = (Events)getEventsHandler();
    final ResourcesImpl resourcesH = (ResourcesImpl)getResourcesHandler();
    final Calendars colsH = (Calendars)getCalendarsHandler();
    String newToken = "";
    BwCalendar resolvedCol = col;

    if (debug()) {
      debug("sync token: " + token + " col: " + resolvedCol.getPath());
    }

    if (col.getTombstoned()) {
      return token;
    }
    
    if (col.getInternalAlias()) {
      resolvedCol = getCalendarsHandler().resolveAlias(col, true, false);
    }
    
    if (resolvedCol.getTombstoned()) {
      return token;
    }

    /* Each collection could be:
     *    a. A calendar collection or special - like Inbox -
     *           only need to look for events.
     *    b. Other collections. Need to look for events, resources and collections.
     */

    final boolean eventsOnly = resolvedCol.getCollectionInfo().onlyCalEntities;

    final Set<EventInfo> evs = eventsH.getSynchEvents(resolvedCol.getPath(), token);

    for (final EventInfo ei: evs) {
      // May be a filtered alias. Remove all those that aren't visible.
      // TODO - if the filter changes this may result in an invalid response. Should force a resynch
      // Could add an earliest valid sync token property.
      
      // TODO - ALso tombstoned items need to be stored in the index.
      // For the moment just let any tombstoned event through
      
      if (!ei.getEvent().getTombstoned() &&
              !eventsH.isVisible(col, ei.getEvent().getName())) {
        continue;
      }
      
      final SynchReportItem sri = new SynchReportItem(vpath, ei);
      items.add(sri);

      if (sri.getToken().compareTo(newToken) > 0) {
        newToken = sri.getToken();
      }
    }

    if (!eventsOnly) {
      // Look for resources
      final List<BwResource> ress = resourcesH.getSynchResources(resolvedCol.getPath(), token);

      for (final BwResource r: ress) {
        final SynchReportItem sri = new SynchReportItem(vpath, r);
        items.add(sri);

        if (sri.getToken().compareTo(newToken) > 0) {
          newToken = sri.getToken();
        }
      }
    }

    final Set<SynchReportItem> colItems = new TreeSet<>();
    final Set<BwCalendar> cols = colsH.getSynchCols(resolvedCol.getPath(), token);
    
    final List<BwCalendar> aliases = new ArrayList<>();

    for (final BwCalendar c: cols) {
      final int calType = c.getCalType();
      
      if (calType == BwCalendar.calTypePendingInbox) {
        continue;
      }

      if ((token != null) && (calType == BwCalendar.calTypeAlias)) {
        aliases.add(c);
        continue;
      }

      final SynchReportItem sri = new SynchReportItem(vpath, c, canSync(c));
      colItems.add(sri);
      items.add(sri);

      if (sri.getToken().compareTo(newToken) > 0) {
        newToken = sri.getToken();
      }

      if (debug()) {
        debug("     token=" + sri.getToken() + " for " + c.getPath());
      }
    }
    
    if (!Util.isEmpty(aliases)) {
      /* Resolve each one and see if the target is a candidate
       */
      for (final BwCalendar c: aliases) {
        final BwCalendar resolved = getCalendarsHandler().resolveAlias(c, true, false);
        
        if (resolved == null) {
          continue;
        }
        
        if (c.getTombstoned() && !getCal().testSynchCol(c, token)) {
          continue;
        }
        
        if (!getCal().testSynchCol(resolved, token)) {
          continue;
        }

        final SynchReportItem sri = new SynchReportItem(vpath, 
                                                        c, 
                                                        canSync(c),
                                                        resolved.getLastmod().getTagValue());
        colItems.add(sri);
        items.add(sri);

        if (sri.getToken().compareTo(newToken) > 0) {
          newToken = sri.getToken();
        }
      }
    }

    if (!recurse) {
      return newToken;
    }

    if (Util.isEmpty(colItems)) {
      return newToken;
    }

    for (final SynchReportItem sri: colItems) {
      if (!sri.getCanSync()) {
        continue;
      }

      final BwCalendar sricol = sri.getCol();
      final String t = getSynchItems(sricol,
                                     Util.buildPath(true, vpath, "/", sricol.getName()),
                                     token, items, true);

      if (t.compareTo(newToken) > 0) {
        newToken = t;
      }
    }

    return newToken;
  }

  /* ====================================================================
   *                   Package private methods
   * ==================================================================== */

  void touchCalendar(final String href) throws CalFacadeException {
    getCal().touchCalendar(href);
  }

  void touchCalendar(final BwCalendar col) throws CalFacadeException {
    getCal().touchCalendar(col);
  }

  PwEncryptionIntf getEncrypter() throws CalFacadeException {
    if (pwEncrypt != null) {
      return pwEncrypt;
    }

    try {
      String pwEncryptClass = "org.bedework.util.security.PwEncryptionDefault";
      //String pwEncryptClass = getSysparsHandler().get().getPwEncryptClass();

      pwEncrypt = (PwEncryptionIntf)CalFacadeUtil.getObject(pwEncryptClass,
                                                            PwEncryptionIntf.class);

      String privKeys = null;
      String pubKeys = null;

      GenKeysMBean gk = (GenKeysMBean)MBeanUtil.getMBean(GenKeysMBean.class,
                                                         GenKeysMBean.serviceName);
      if (gk != null) {
        privKeys = gk.getPrivKeyFileName();
        pubKeys = gk.getPublicKeyFileName();
      }

      if (privKeys == null) {
        throw new CalFacadeException("Unable to get keyfile locations. Is genkeys service installed?");
      }

      pwEncrypt.init(privKeys, pubKeys);

      return pwEncrypt;
    } catch (CalFacadeException cfe) {
      cfe.printStackTrace();
      throw cfe;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new CalFacadeException(t);
    }
  }

  /* Get current parameters
   */
  CalSvcIPars getPars() {
    return pars;
  }

  /* See if in public admin mode
   */
  private boolean isPublicAdmin() throws CalFacadeException {
    return pars.getPublicAdmin();
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
