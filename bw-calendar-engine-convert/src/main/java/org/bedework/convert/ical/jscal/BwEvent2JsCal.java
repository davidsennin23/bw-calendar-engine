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
package org.bedework.convert.ical.jscal;

import org.bedework.calfacade.BwAttachment;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.convert.DifferResult;
import org.bedework.jsforj.impl.JSFactory;
import org.bedework.jsforj.impl.values.JSLocalDateTimeImpl;
import org.bedework.jsforj.model.JSCalendarObject;
import org.bedework.jsforj.model.JSProperty;
import org.bedework.jsforj.model.JSPropertyNames;
import org.bedework.jsforj.model.JSTypes;
import org.bedework.jsforj.model.values.JSOverride;
import org.bedework.jsforj.model.values.JSValue;
import org.bedework.jsforj.model.values.UnsignedInteger;
import org.bedework.jsforj.model.values.collections.JSRecurrenceOverrides;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.XcalUtil;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TextList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.parameter.RelType;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.parameter.XParameter;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Contact;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.FreeBusy;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RelatedTo;
import net.fortuna.ical4j.model.property.Resources;

import java.net.URI;
import java.text.ParseException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.bedework.convert.ical.jscal.BwDiffer.differs;
import static org.bedework.util.misc.response.Response.Status.failed;

/** Class to provide utility methods for translating to VEvent ical4j classes
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public class BwEvent2JsCal {
  final static JSFactory factory = JSFactory.getFactory();

  private static BwLogger logger =
          new BwLogger().setLoggedClass(BwEvent2JsCal.class);

  /** Make a Jscalendar object from a BwEvent object.
   *
   * @param ei the event or override we are converting
   * @param master - if non-null ei is an override
   * @param jsCalMaster - must be non-null if master is non-null
   * @param tzreg - timezone registry
   * @param currentPrincipal - href for current authenticated user
   * @return Response with status and EventInfo object representing new entry or updated entry
   */
  public static GetEntityResponse<JSValue> convert(
          final EventInfo ei,
          final EventInfo master,
          final JSCalendarObject jsCalMaster,
          final TimeZoneRegistry tzreg,
          final String currentPrincipal) {
    var resp = new GetEntityResponse<JSValue>();

    if ((ei == null) || (ei.getEvent() == null)) {
      return Response.notOk(resp, failed, "No entity supplied");
    }

    final BwEvent val = ei.getEvent();

    boolean isInstance = false;

    try {
      /*
      Component xcomp = null;
      Calendar cal = null;

      final List<BwXproperty> xcompProps = val.getXproperties(BwXproperty.bedeworkIcal);
      if (!Util.isEmpty(xcompProps)) {
        final BwXproperty xcompProp = xcompProps.get(0);
        final String xcompPropVal = xcompProp.getValue();

        if (xcompPropVal != null) {
          final StringBuilder sb = new StringBuilder();
          final Icalendar ic = new Icalendar();

          try {
            sb.append("BEGIN:VCALENDAR\n");
            sb.append(Version.VERSION_2_0.toString());
            sb.append("\n");
            sb.append(xcompPropVal);
            if (!xcompPropVal.endsWith("\n")) {
              sb.append("\n");
            }
            sb.append("END:VCALENDAR\n");

            CalendarBuilder bldr = new CalendarBuilder(new CalendarParserImpl(), ic);

            UnfoldingReader ufrdr = new UnfoldingReader(new StringReader(sb.toString()),
                                                        true);

            cal = bldr.build(ufrdr);
          } catch (Throwable t) {
            logger.error(t);
            logger.error("Trying to parse:\n" + xcompPropVal);
          }
        }
      }
      */

      boolean freeBusy = false;
      boolean vavail = false;
      boolean todo = false;
      boolean event = false;
      boolean vpoll = false;
      final String jstype;

      switch (val.getEntityType()) {
        case IcalDefs.entityTypeEvent:
          jstype = JSTypes.typeJSEvent;
          event = true;
          break;

        case IcalDefs.entityTypeTodo:
          jstype = JSTypes.typeJSTask;
          todo = true;
          break;
/*
        case IcalDefs.entityTypeJournal:
          jstype = JSTypes.typeJSJournal;
          break;

        case IcalDefs.entityTypeFreeAndBusy:
          jstype = JSTypes.typeJSFreeBusy;
          freeBusy = true;
          break;

        case IcalDefs.entityTypeVavailability:
          jstype = JSTypes.typeJSAvailability;
          vavail = true;
          break;

        case IcalDefs.entityTypeAvailable:
          jstype = JSTypes.typeJSAvailable;
          break;

        case IcalDefs.entityTypeVpoll:
          jstype = JSTypes.typeJSVPoll;
          vpoll = true;
          break;
*/
        default:
          return Response.error(resp, "org.bedework.invalid.entity.type: " +
                  val.getEntityType());
      }

      final JSCalendarObject jsval;
      final JSRecurrenceOverrides ovs;
      JSProperty ovprop = null;
      JSOverride override = null;

      if (master == null) {
        jsval = (JSCalendarObject)factory.newValue(jstype);
        ovs = null;
      } else {
        jsval = null;
        ovs = jsCalMaster.getOverrides(true);
      }

      Property prop;

      /* ------------------- RecurrenceID --------------------
       * Done early to verify if this is an instance.
       */

      String strval = val.getRecurrenceId();
      if ((strval != null) && (strval.length() > 0)) {
        var rid = new JSLocalDateTimeImpl(jsonDate(strval));
        isInstance = true;

        if (master == null) {
          // A standalone recurrence instance
          jsval.addProperty(JSPropertyNames.recurrenceId,
                            rid.getStringValue());
        } else {
          // Create an override.
          ovprop = ovs.makeOverride(rid.getStringValue());

          override = (JSOverride)ovprop.getValue();
        }
      }

      /* ------------------- Alarms -------------------- */
      //VAlarmUtil.processEventAlarm(val, comp, currentPrincipal);

      /* ------------------- Attachments -------------------- */

      final Set<BwAttachment> atts = val.getAttachments();
      final DifferResult<BwAttachment, ?> attDiff =
              differs(BwAttachment.class,
                      PropertyInfoIndex.ATTACH,
                      atts, master);
      if (attDiff.differs) {
        if ((val.getNumAttachments() == 0) &
                (master != null)) {
          // Override removing all attachments

        }
      } else if (val.getNumAttachments() > 0) {
        for (BwAttachment att : val.getAttachments()) {
          //pl.add(setAttachment(att));
        }
      }

      /* ------------------- Attendees -------------------- */
      /*
      if (!vpoll && (val.getNumAttendees() > 0)) {
        for (BwAttendee att: val.getAttendees()) {
          prop = setAttendee(att);
          mergeXparams(prop, xcomp);
          pl.add(prop);
        }
      }
       */

      /* ------------------- Categories -------------------- */

      if (val.getNumCategories() > 0) {
        /* This event has a category - do each one separately */

        // LANG - filter on language - group language in one cat list?
        for (BwCategory cat: val.getCategories()) {
          prop = new Categories();
          TextList cl = ((Categories)prop).getCategories();

          cl.add(cat.getWord().getValue());

//          pl.add(langProp(prop, cat.getWord()));
        }
      }

      /* ------------------- Class -------------------- */

      final String clazz = val.getClassification();
      final DifferResult<String, ?> classDiff =
              differs(String.class,
                      PropertyIndex.PropertyInfoIndex.CLASS,
                      clazz, master);
      if (classDiff.differs) {
        if (clazz.equalsIgnoreCase("confidential")) {
          jsval.setProperty(JSPropertyNames.privacy, "secret");
        } else {
          jsval.setProperty(JSPropertyNames.privacy,
                            clazz.toLowerCase());
        }
      }

      /* ------------------- Comments -------------------- */

      final var comments = val.getComments();
      final DifferResult<BwString, ?> commDiff =
              differs(BwString.class,
                      PropertyInfoIndex.COMMENT,
                      comments, master);
      if (commDiff.differs) {
        for (final BwString str: val.getComments()) {
          jsval.addComment(str.getValue());
        }
      }

      /* ------------------- Completed -------------------- */
      if (todo || vpoll) {
        final var completed = val.getCompleted();
        final DifferResult<BwString, ?> compDiff =
                differs(BwString.class,
                        PropertyInfoIndex.COMPLETED,
                        completed, master);
        if (compDiff.differs) {
          jsval.setProperty(JSPropertyNames.progress, "completed");
          jsval.setProperty(JSPropertyNames.progressUpdated,
                            jsonDate(completed));
        }
      }
      /* ------------------- Contact -------------------- */

      var contacts = val.getContacts();
      final DifferResult<BwContact, ?> contDiff =
              differs(BwContact.class,
                      PropertyInfoIndex.CONTACT,
                      contacts, master);
      if (contDiff.differs) {
        for (final BwContact c: contacts) {
          // LANG
          prop = new Contact(c.getCn().getValue());
          final String l = c.getLink();

          throw new RuntimeException("Not done");
/*          if (l != null) {
            prop.getParameters().add(new AltRep(l));
          }
          throw new RuntimeException("Not done");
          pl.add(langProp(uidProp(prop, c.getUid()), c.getCn()));
 */
        }
      }

      /* ------------------- Cost -------------------- */

      if (val.getCost() != null) {
        addXproperty(jsval, master, BwXproperty.bedeworkCost,
                     null, val.getCost());
      }

      /* ------------------- Created -------------------- */

      if ((master == null) && // Suppressed for overrides
          val.getCreated() != null) {
        jsval.setProperty(JSPropertyNames.created,
                          jsonDate(val.getCreated()));
      }

      /* ------------------- Deleted -------------------- */

      if (val.getDeleted()) {
        addXproperty(jsval, master, BwXproperty.bedeworkDeleted,
                     null, String.valueOf(val.getDeleted()));
      }

      /* ------------------- Description -------------------- */

      BwStringBase<?> bwstr = val.findDescription(null);
      if (bwstr != null) {
        throw new RuntimeException("Not done");
        //pl.add(langProp(new Description(bwstr.getValue()), bwstr));
      }


      /* ------------------- DtStamp -------------------- */
      /* ------------------- LastModified -------------------- */

      if (master == null) {
        var dtstamp = val.getDtstamp();
        var lastMod = val.getLastmod();
        final String updatedVal;

        var updCmp = Util.cmpObjval(dtstamp, lastMod);
        if (updCmp <= 0) {
          updatedVal = dtstamp;
        } else {
          updatedVal = lastMod;
        }

        if (updatedVal != null) {
          jsval.setProperty(JSPropertyNames.created,
                            jsonDate(updatedVal));
        }
      }

      /* ------------------- DtStart -------------------- */

      if (!val.getNoStart()) {
        DtStart dtstart = val.getDtstart().makeDtStart(tzreg);
        if (freeBusy | val.getForceUTC()) {
          dtstart.setUtc(true);
        }
        throw new RuntimeException("Not done");
        //pl.add(dtstart);
      }
      /* ------------------- Due/DtEnd/Duration --------------------
      */

      if (val.getEndType() == StartEndComponent.endTypeDate) {
        if (todo) {
          Due due = val.getDtend().makeDue(tzreg);
          if (freeBusy | val.getForceUTC()) {
            due.setUtc(true);
          }
          throw new RuntimeException("Not done");
          //pl.add(due);
        } else {
          DtEnd dtend = val.getDtend().makeDtEnd(tzreg);
          if (freeBusy | val.getForceUTC()) {
            dtend.setUtc(true);
          }
          throw new RuntimeException("Not done");
          //pl.add(dtend);
        }
      } else if (val.getEndType() == StartEndComponent.endTypeDuration) {
        throw new RuntimeException("Not done");
        //addProperty(comp, new Duration(new Dur(val.getDuration())));
      }

      /* ------------------- ExDate --below------------ */
      /* ------------------- ExRule --below------------- */

      if (freeBusy) {
        Collection<BwFreeBusyComponent> fbps = val.getFreeBusyPeriods();

        if (fbps != null) {
          for (BwFreeBusyComponent fbc: fbps) {
            FreeBusy fb = new FreeBusy();

            throw new RuntimeException("Not done");
/*            int type = fbc.getType();
            if (type == BwFreeBusyComponent.typeBusy) {
              addParameter(fb, FbType.BUSY);
            } else if (type == BwFreeBusyComponent.typeFree) {
              addParameter(fb, FbType.FREE);
            } else if (type == BwFreeBusyComponent.typeBusyUnavailable) {
              addParameter(fb, FbType.BUSY_UNAVAILABLE);
            } else if (type == BwFreeBusyComponent.typeBusyTentative) {
              addParameter(fb, FbType.BUSY_TENTATIVE);
            } else {
              throw new CalFacadeException("Bad free-busy type " + type);
            }

            PeriodList pdl =  fb.getPeriods();

            for (Period p: fbc.getPeriods()) {
              // XXX inverse.ca plugin cannot handle durations.
              Period np = new Period(p.getStart(), p.getEnd());
              pdl.add(np);
            }

            pl.add(fb);
 */
          }
        }

      }

      /* ------------------- Geo -------------------- */

      if (!vpoll) {
        BwGeo bwgeo = val.getGeo();
        if (bwgeo != null) {
          Geo geo = new Geo(bwgeo.getLatitude(), bwgeo.getLongitude());
          throw new RuntimeException("Not done");
          //pl.add(geo);
        }
      }

      /* ------------------- Location -------------------- */

      if (!vpoll) {
        final BwLocation loc = val.getLocation();
        if (loc != null) {
          prop = new Location(loc.getCombinedValues());

          throw new RuntimeException("Not done");
          /*
          pl.add(langProp(uidProp(prop, loc.getUid()), loc.getAddress()));

          addXproperty(pl, BwXproperty.xBedeworkLocationAddr,
                       null, loc.getAddressField());
          addXproperty(pl, BwXproperty.xBedeworkLocationRoom,
                       null, loc.getRoomField());
          addXproperty(pl, BwXproperty.xBedeworkLocationAccessible,
                       null, String.valueOf(loc.getAccessible()));
          addXproperty(pl, BwXproperty.xBedeworkLocationSfield1,
                       null, loc.getSubField1());
          addXproperty(pl, BwXproperty.xBedeworkLocationSfield2,
                       null, loc.getSubField2());
          addXproperty(pl, BwXproperty.xBedeworkLocationGeo,
                       null, loc.getGeouri());
          addXproperty(pl, BwXproperty.xBedeworkLocationStreet,
                       null, loc.getStreet());
          addXproperty(pl, BwXproperty.xBedeworkLocationCity,
                       null, loc.getCity());
          addXproperty(pl, BwXproperty.xBedeworkLocationState,
                       null, loc.getState());
          addXproperty(pl, BwXproperty.xBedeworkLocationZip,
                       null, loc.getZip());
          addXproperty(pl, BwXproperty.xBedeworkLocationLink,
                       null, loc.getLink());
*/
        }
      }

      /* ------------------- Organizer -------------------- */

      BwOrganizer org = val.getOrganizer();
      if (org != null) {
        throw new RuntimeException("Not done");
/*        prop = setOrganizer(org);
        mergeXparams(prop, xcomp);
        pl.add(prop);
        */
      }

      /* ------------------- PercentComplete -------------------- */

      if (todo) {
        Integer pc = val.getPercentComplete();
        if (pc != null) {
          throw new RuntimeException("Not done");
          //pl.add(new PercentComplete(pc));
        }
      }

      /* ------------------- Priority -------------------- */

      Integer prio = val.getPriority();
      if (prio != null) {
        throw new RuntimeException("Not done");
        //pl.add(new Priority(prio));
      }

      /* ------------------- RDate -below------------------- */

      /* ------------------- RelatedTo -------------------- */

      /* We encode related to (maybe) as triples - reltype, value-type, value */

      String[] info = null;

      BwRelatedTo relto = val.getRelatedTo();
      if (relto != null) {
        info = new String[3];

        info[0] = relto.getRelType();
        info[1] = ""; // default
        info[2] = relto.getValue();
      } else {
        String relx = val.getXproperty(BwXproperty.bedeworkRelatedTo);

        if (relx != null) {
          info = Util.decodeArray(relx);
        }
      }

      if (info != null) {
        int i = 0;

        while (i < info.length) {
          RelatedTo irelto;

          String reltype = info[i];
          String valtype = info[i + 1];
          String relval = info[i + 2];

          ParameterList rtpl = null;
          if ((reltype != null) && (reltype.length() > 0)) {
            rtpl = new ParameterList();
            rtpl.add(new RelType(reltype));
          }

          if (valtype.length() > 0) {
            if (rtpl == null) {
              rtpl = new ParameterList();
            }
            rtpl.add(new Value(valtype));
          }

          if (rtpl != null) {
            irelto = new RelatedTo(rtpl, relval);
          } else {
            irelto = new RelatedTo(relval);
          }

          i += 3;
          throw new RuntimeException("Not done");
          //pl.add(irelto);
        }
      }

      /* ------------------- Resources -------------------- */

      if (val.getNumResources() > 0) {
        /* This event has a resource */

        prop = new Resources();
        TextList rl = ((Resources)prop).getResources();

        for (BwString str: val.getResources()) {
          // LANG
          rl.add(str.getValue());
        }

        throw new RuntimeException("Not done");
        //pl.add(prop);
      }

      /* ------------------- RRule -below------------------- */

      /* ------------------- Sequence -------------------- */

      if (val.getSequence() > 0) {
        jsval.setProperty(JSPropertyNames.sequence,
                          new UnsignedInteger(val.getSequence()));
      }

      /* ------------------- Status -------------------- */

      String status = val.getStatus();
      if ((status != null) && !status.equals(BwEvent.statusMasterSuppressed)) {
        throw new RuntimeException("Not done");
        //pl.add(new Status(status));
      }

      /* ------------------- Summary -------------------- */

      var summary = val.getSummary();
      final DifferResult<String, ?> sumDiff =
              differs(String.class,
                      PropertyInfoIndex.SUMMARY,
                      summary, master);
      if (sumDiff.differs) {
        jsval.setProperty(JSPropertyNames.title, summary);
      }

      /* ------------------- Transp -------------------- */

      if (!todo && !vpoll) {
        strval = val.getPeruserTransparency(currentPrincipal);

        final DifferResult<String, ?> transpDiff =
                differs(String.class,
                        PropertyInfoIndex.TRANSP,
                        strval, master);
        if (transpDiff.differs) {
          if (strval.equalsIgnoreCase("opaque")) {
            jsval.setProperty(JSPropertyNames.freeBusyStatus, "busy");
          } else {
            jsval.setProperty(JSPropertyNames.freeBusyStatus,
                              "free");
          }
        }
      }

      /* ------------------- Uid -------------------- */

      if (master == null) {
        jsval.setUid(val.getUid());
      }

      /* ------------------- Url -------------------- */

      strval = val.getLink();

      if (strval != null) {
        // Possibly drop this if we do it on input and check all data
        strval = strval.trim();
      }

      final DifferResult<String, ?> urlDiff =
              differs(String.class,
                      PropertyInfoIndex.URL,
                      strval, master);
      if (urlDiff.differs) {
        URI uri = Util.validURI(strval);
        if (uri != null) {
          throw new RuntimeException("Not done");
          //pl.add(new Url(uri));
        }
      }

      /* ------------------- X-PROPS -------------------- */

      if (val.getNumXproperties() > 0) {
        /* This event has x-props */

        try {
          throw new RuntimeException("Not done");
          //xpropertiesToIcal(pl, val.getXproperties());
        } catch (Throwable t) {
          // XXX For the moment swallow these.
          logger.error(t);
        }
      }

      /* ------------------- Overrides -------------------- */

/*
      throw new RuntimeException("Not done");
      if (!vpoll && !isInstance && !isOverride && val.testRecurring()) {
        doRecurring(val, pl);
      }
 */
      /* ------------------- Available -------------------- */

      if (vavail) {
        throw new RuntimeException("Not done");
/*        if (ei.getNumContainedItems() > 0) {
          final VAvailability va = (VAvailability)comp;
          for (final EventInfo aei: ei.getContainedItems()) {
            va.getAvailable().add((Available)toIcalComponent(aei, false, tzreg,
                                                  currentPrincipal));
          }
        }

        /* ----------- Vavailability - busyType ----------------- */

/*
        String s = val.getBusyTypeString();
        if (s != null) {
          throw new RuntimeException("Not done");
          //pl.add(new BusyType(s));
        }

 */
      }

      /* ------------------- Vpoll -------------------- */

      //if (!vpoll && (val.getPollItemId() != null)) {
      //  pl.add(new PollItemId(val.getPollItemId()));
     // }

      final List<BwXproperty> xlocs =
              val.getXproperties(BwXproperty.xBedeworkLocation);

      /*
      throw new RuntimeException("Not done");
      if (!Util.isEmpty(xlocs) &&
              (comp.getProperty(Property.LOCATION) == null)) {
        // Create a location from the x-property
        final BwXproperty xloc = xlocs.get(0);

        final Location loc = new Location(xloc.getValue());

        comp.getProperties().add(loc);
      }

      if (vpoll) {
        final Integer ival = val.getPollWinner();

        if (ival != null) {
          pl.add(new PollWinner(ival));
        }

        strval = val.getPollAcceptResponse();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new AcceptResponse(strval));
        }

        strval = val.getPollMode();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new PollMode(strval));
        }

        strval = val.getPollProperties();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new PollProperties(strval));
        }

        final Map<String, Participant> voters = parseVpollVoters(val);

        for (final Participant v: voters.values()) {
          ((VPoll)comp).getVoters().add(v);
        }

        final Map<Integer, Component> comps = parseVpollCandidates(val);

        for (final Component candidate: comps.values()) {
          ((VPoll)comp).getCandidates().add(candidate);
        }
      }
       */

      throw new RuntimeException("Not done");
      //return comp;
//    } catch (final CalFacadeException cfe) {
//      throw cfe;
    } catch (final Throwable t) {
//      throw new CalFacadeException(t);
    }
    throw new RuntimeException("Not done");
  }

  /** Build recurring properties from event.
   *
   * @param val event
   * @param pl properties
   * @throws RuntimeException for bad date values
   */
  public static void doRecurring(final BwEvent val,
                                 final PropertyList pl) {
    try {
      if (val.hasRrules()) {
        for(String s: val.getRrules()) {
          RRule rule = new RRule();
          rule.setValue(s);

          pl.add(rule);
        }
      }

      if (val.hasExrules()) {
        for(String s: val.getExrules()) {
          ExRule rule = new ExRule();
          rule.setValue(s);

          pl.add(rule);
        }
      }

      makeDlp(val, false, val.getRdates(), pl);

      makeDlp(val, true, val.getExdates(), pl);
    } catch (final ParseException pe) {
      throw new RuntimeException(pe);
    }
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private static void mergeXparams(final Property p, final Component c) {
    if (c == null) {
      return;
    }

    PropertyList pl = c.getProperties(p.getName());

    if (Util.isEmpty(pl)) {
      return;
    }

    String pval = p.getValue();

    if (p instanceof Attach) {
      // We just saved the hash for binary content
      Value v = (Value)p.getParameter(Parameter.VALUE);

      if (v != null) {
        pval = String.valueOf(pval.hashCode());
      }
    }

    Property from = null;

    if (pl.size() == 1) {
      from = pl.get(0);
    } else {
      // Look for value?
      for (final Object aPl : pl) {
        from = (Property)aPl;
        if (from.getValue().equals(pval)) {
          break;
        }
      }
    }

    if (from == null) {
      return;
    }

    ParameterList params = from.getParameters();

    final Iterator<Parameter> parit = params.iterator();
    while (parit.hasNext()) {
      Parameter param = parit.next();

      if (!(param instanceof XParameter)) {
        continue;
      }

      XParameter xpar = (XParameter)param;

      if (xpar.getName().toUpperCase().equals(BwXproperty.xparUid)) {
        continue;
      }

      p.getParameters().add(xpar);
    }
  }

  private static Property uidProp(final Property prop, final String uid) {
    Parameter par = new XParameter(BwXproperty.xparUid, uid);

    prop.getParameters().add(par);

    return prop;
  }

  private static Property langProp(final Property prop, final BwStringBase s) {
    Parameter par = s.getLangPar();

    if (par != null) {
      prop.getParameters().add(par);
    }

    return prop;
  }

  private static void makeDlp(final BwEvent val,
                              final boolean exdt,
                              final Collection<BwDateTime> dts,
                              final PropertyList pl) throws ParseException {
    if ((dts == null) || (dts.isEmpty())) {
      return;
    }

    TimeZone tz = null;
    if (!val.getForceUTC()) {
      BwDateTime dtstart = val.getDtstart();

      if ((dtstart != null) && !dtstart.isUTC()) {
        DtStart ds = dtstart.makeDtStart();
        tz = ds.getTimeZone();
      }
    }

    /* Generate as one date per property - matches up to other vendors better */
    for (BwDateTime dt: dts) {
      DateList dl;

      /* Always use the UTC values */
      boolean dateType = false;

      if (dt.getDateType()) {
        dl = new DateList(Value.DATE);
        dl.setUtc(true);
        dateType = true;
        dl.add(new Date(dt.getDtval()));
      } else {
        dl = new DateList(Value.DATE_TIME);

        if (tz == null) {
          dl.setUtc(true);
          DateTime dtm = new DateTime(dt.getDate());
          dtm.setUtc(true);
          dl.add(dtm);
        } else {
          dl.setTimeZone(tz);
          DateTime dtm = new DateTime(dt.getDate());
          dtm.setTimeZone(tz);
          dl.add(dtm);
        }
      }

      DateListProperty dlp;

      if (exdt) {
        dlp = new ExDate(dl);
      } else {
        dlp = new RDate(dl);
      }

      if (dateType) {
        dlp.getParameters().add(Value.DATE);
      } else if (tz != null) {
        dlp.setTimeZone(tz);
      }

      pl.add(dlp);
    }
  }

  private static Date makeZonedDt(final BwEvent val,
                                  final String dtval) throws Throwable {
    BwDateTime dtstart = val.getDtstart();

    DateTime dt = new DateTime(dtval);

    if (dtstart.getDateType()) {
      // RECUR - fix all day recurrences sometime
      if (dtval.length() > 8) {
        // Try to fix up bad all day recurrence ids. - assume a local timezone
        dt.setTimeZone(null);
        return new Date(dt.toString().substring(0, 8));
      }

      return dt;
    }

    if (val.getForceUTC()) {
      return dt;
    }

    if (!dtstart.isUTC()) {
      DtStart ds = dtstart.makeDtStart();
      dt.setTimeZone(ds.getTimeZone());
    }

    return dt;
  }

     /*
  private String makeContactString(BwSponsor sp) {
    if (pars.simpleContact) {
      return sp.getName();
    }

    StringBuilder sb = new StringBuilder(sp.getName());
    addNonNull(defaultDelim, Resources.PHONENBR, sp.getPhone(), sb);
    addNonNull(defaultDelim, Resources.EMAIL, sp.getEmail(), sb);
    addNonNull(urlDelim, Resources.URL, sp.getLink(), sb);

    if (sb.length() == 0) {
      return null;
    }

    return sb.toString();
  }

  /* * Build a location string value from the location.
   *
   * <p>We try to build something we can parse later.
   * /
  private String makeLocationString(BwLocation loc) {
    if (pars.simpleLocation) {
      return loc.getAddress();
    }

    StringBuilder sb = new StringBuilder(loc.getAddress());
    addNonNull(defaultDelim, Resources.SUBADDRESS, loc.getSubaddress(), sb);
    addNonNull(urlDelim, Resources.URL, loc.getLink(), sb);

    if (sb.length() == 0) {
      return null;
    }

    return sb.toString();
  }
  */

  private static String jsonDate(final String val) {
    return XcalUtil.getXmlFormatDateTime(val);
  }

  /**
   * @param jscal current entity
   * @param master non-null if jscal is an override
   * @param name of xprop
   * @param pars List of Xpar
   * @param val new value
   */
  public static void addXproperty(final JSCalendarObject jscal,
                                  final EventInfo master,
                                  final String name,
                                  final List<BwXproperty.Xpar> pars,
                                  final String val) {
    if (val == null) {
      return;
    }
    throw new RuntimeException("Not done");
//    pl.add(new XProperty(name, makeXparlist(pars), val));
  }
}

