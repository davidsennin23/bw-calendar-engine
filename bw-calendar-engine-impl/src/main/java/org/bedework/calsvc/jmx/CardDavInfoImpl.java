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
package org.bedework.calsvc.jmx;

import org.bedework.calfacade.configs.CardDavInfo;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;

/** Information to access carddav
 *
 * @author Mike Douglass
 */
@ConfInfo(elementName = "carddav-info",
          type = "org.bedework.calfacade.configs.CardDavInfo")
public class CardDavInfoImpl extends ConfigBase<CardDavInfoImpl>
        implements CardDavInfo {
  private boolean auth;

  private String url;

  private String cutypeMapping;

  @Override
  public void setAuth(final boolean val) {
    auth = val;
  }

  @Override
  public boolean getAuth() {
    return auth;
  }

  @Override
  public void setUrl(final String val) {
    url = val;
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public void setCutypeMapping(final String val) {
    cutypeMapping = val;
  }

  @Override
  public String getCutypeMapping() {
    return cutypeMapping;
  }
}
