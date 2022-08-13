/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.util;

import com.mapr.web.security.SslConfig;
import com.mapr.web.security.WebSecurityManager;

public class MapRSslConfigReader {

    private MapRSslConfigReader() {
        // no initialization
    }

    /**
     * Reads client keystore location.
     * @return client keystore location as string.
     */
    public static String getClientKeystoreLocation() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_CLIENT_ONLY)) {
            return sslConfig.getClientKeystoreLocation();
        }
    }

    /**
     * Reads client keystore password value.
     * @return client keystore password value as string.
     */
    public static String getClientKeystorePassword() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_CLIENT_ONLY)) {
            return new String(sslConfig.getClientKeystorePassword());
        }
    }

    /**
     * Reads client key password value.
     * @return client key password value as string.
     */
    public static String getClientKeyPassword() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_CLIENT_ONLY)) {
            return new String(sslConfig.getClientKeyPassword());
        }
    }

    /**
     * Reads server keystore location.
     * @return server keystore location as string.
     */
    public static String getServerKeystoreLocation() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_ALL)) {
            return sslConfig.getServerKeystoreLocation();
        }
    }

    /**
     * Reads server keystore password value.
     * @return server keystore password value as string.
     */
    public static String getServerKeystorePassword() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_ALL)) {
            return new String(sslConfig.getServerKeystorePassword());
        }
    }

    /**
     * Reads server key password value.
     * @return server key password value as string.
     */
    public static String getServerKeyPassword() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_ALL)) {
            return new String(sslConfig.getServerKeyPassword());
        }
    }

    /**
     * Reads server key store type value.
     * @return server key store type value as string.
     */
    public static String getServerKeystoreType() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_ALL)) {
            return sslConfig.getServerKeystoreType();
        }
    }

    /**
     * Reads server truststore location.
     * @return server truststore location as string.
     */
    public static String getServerTruststoreLocation() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_ALL)) {
            return sslConfig.getServerTruststoreLocation();
        }
    }

    /**
     * Reads server truststore password.
     * @return server truststore password as string.
     */
    public static String getServerTruststorePassword() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_ALL)) {
            return new String(sslConfig.getServerTruststorePassword());
        }
    }

    /**
     * Reads server truststore type.
     * @return server truststore type as string.
     */
    public static String getServerTruststoreType() {
        try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_ALL)) {
            return sslConfig.getServerTruststoreType();
        }
    }

}
