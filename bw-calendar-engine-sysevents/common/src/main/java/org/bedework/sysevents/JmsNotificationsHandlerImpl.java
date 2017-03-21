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
package org.bedework.sysevents;

import org.bedework.sysevents.events.SysEventBase;
import org.bedework.sysevents.events.SysEventBase.Attribute;
import org.bedework.sysevents.listeners.SysEventListener;

import org.apache.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;

/**
 * This is the implementation of a notifications handler which sends jms
 * messages.
 *
 * @author Mike Douglass douglm - rpi.edu
 */
class JmsNotificationsHandlerImpl extends NotificationsHandler implements
    JmsDefs {
  private transient Logger log;

  /* Default sysevents queue - everything goes here */

  private JmsConnectionHandler conn;

  private MessageProducer sender;

  /*
   * We could use the activemq camel support (I think) to filter out certain
   * events and send them on to another queue.
   */

  private boolean debug;

  JmsNotificationsHandlerImpl() throws NotificationException {
    debug = getLogger().isDebugEnabled();

    conn = new JmsConnectionHandler();

    conn.open(syseventsQueueName);

    sender = conn.getProducer();
  }

  private static long sends = 0;
  private static long sendTime = 0;

  @Override
  public void post(final SysEventBase ev) throws NotificationException {
    if (debug) {
      trace(ev.toString());
    }

    try {
      final ObjectMessage msg = conn.getSession().createObjectMessage();

      msg.setObject(ev);

      for (final Attribute attr: ev.getMessageAttributes()) {
        msg.setStringProperty(attr.name, attr.value);
      }

      long start = System.currentTimeMillis();
      sender.send(msg);
      sends++;
      sendTime += System.currentTimeMillis() - start;

//      if ((sends % 100) == 0) {
//        System.out.println("Sends: " + sends + " avg: " + sendTime / sends);
//      }
    } catch (final JMSException je) {
      throw new NotificationException(je);
    }
  }

  @Override
  public void registerListener(final SysEventListener l,
                               final boolean persistent)
          throws NotificationException {

  }

  @Override
  public void removeListener(final SysEventListener l)
          throws NotificationException {

  }

  /*
   * ====================================================================
   * Protected methods
   * ====================================================================
   */

  protected void info(final String msg) {
    getLogger().info(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  /*
   * Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}