/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.security.oidc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.authentication.util.SsoConfigurationUtil;
import org.springframework.core.env.AbstractEnvironment;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import static org.apache.ranger.security.oidc.OidcUtil.IS_OIDC_ENABLED;

public class SpringProfileActivatorListener implements ServletContextListener {
  @Override
  public void contextInitialized(ServletContextEvent sce) {
    System.setProperty(AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME,
            IS_OIDC_ENABLED ? "oidc-login" : "classic-login");
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {

  }
}
