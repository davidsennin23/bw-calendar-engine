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
/*
 Copyright (c) 2000-2005 University of Washington.  All rights reserved.

 Redistribution and use of this distribution in source and binary forms,
 with or without modification, are permitted provided that:

   The above copyright notice and this permission notice appear in
   all copies and supporting documentation;

   The name, identifiers, and trademarks of the University of Washington
   are not used in advertising or publicity without the express prior
   written permission of the University of Washington;

   Recipients acknowledge that this distribution is made available as a
   research courtesy, "as is", potentially with defects, without
   any obligation on the part of the University of Washington to
   provide support, services, or repair;

   THE UNIVERSITY OF WASHINGTON DISCLAIMS ALL WARRANTIES, EXPRESS OR
   IMPLIED, WITH REGARD TO THIS SOFTWARE, INCLUDING WITHOUT LIMITATION
   ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
   PARTICULAR PURPOSE, AND IN NO EVENT SHALL THE UNIVERSITY OF
   WASHINGTON BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
   DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
   PROFITS, WHETHER IN AN ACTION OF CONTRACT, TORT (INCLUDING
   NEGLIGENCE) OR STRICT LIABILITY, ARISING OUT OF OR IN CONNECTION WITH
   THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.bedework.calcorei;

import org.bedework.calfacade.exc.CalFacadeException;

/** Obtain a Calintf object.
 *
 */
public class CalintfFactory {
  public final static Class hibernateClass;
  public final static Class indexerOnlyClass;
  static {
    try {
      /* CalIntf implemented in hibernate */
      hibernateClass =
            Class.forName("org.bedework.calcore.hibernate.CalintfImpl");

      indexerOnlyClass =
              Class.forName("org.bedework.calcore.common.CalintfROImpl");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* CalIntf implemented via CalDAV */
  //public final static String caldavClass =
  // "org.bedework.calcore.caldav.CalintfCaldavImpl";

  /** Obtain a calintf object.
   *
   * @param cl the class
   * @return Calintf
   * @throws CalFacadeException on error
   */
  public static Calintf getIntf(final Class cl) throws CalFacadeException {
    try {
      Object o = cl.newInstance();

      if (o == null) {
        throw new CalFacadeException("Class " + cl + " not found");
      }

      if (!(o instanceof Calintf)) {
        throw new CalFacadeException("Class " + cl +
                                     " is not a subclass of " +
                                     Calintf.class.getName());
      }

      return (Calintf)o;
    } catch (CalFacadeException ce) {
      throw ce;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }
}
