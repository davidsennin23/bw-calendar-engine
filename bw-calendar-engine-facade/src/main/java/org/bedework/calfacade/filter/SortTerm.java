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
package org.bedework.calfacade.filter;

import edu.rpi.cmt.calendar.PropertyIndex.PropertyInfoIndex;
import edu.rpi.sss.util.ToString;

/** Define a field we sort by
 *
 * @author Mike Douglass
 * @version 1.0
 */

public class SortTerm {
  private PropertyInfoIndex index;

  private boolean ascending;

  public SortTerm(final PropertyInfoIndex index,
                  final boolean ascending) {
    this.index = index;
    this.ascending = ascending;
  }

  /**
   * @return property name
   */
  public PropertyInfoIndex getIndex() {
    return index;
  }

  /**
   * @return true for acending.
   */
  public boolean isAscending() {
    return ascending;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append(getIndex());
    ts.append("ascending", isAscending());

    return ts.toString();
  }
}