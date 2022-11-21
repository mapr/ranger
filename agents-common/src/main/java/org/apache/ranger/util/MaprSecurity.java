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

package org.apache.ranger.util;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class MaprSecurity {
    private static final Logger LOG = LoggerFactory.getLogger(MaprSecurity.class);
    private static final String MAPR_HOME = findMapRHome();
    public static final String SECURITY_TYPE_PROPERTY = "ranger.security.type";
    public static final String MAPR_SASL = "maprsasl";
    public static final String KERBEROS = "kerberos";
    public static final String NONE = "none";
    private static final String CLUSTERS_FILE = MAPR_HOME + "/conf/mapr-clusters.conf";
    private static String SECURITY_TYPE_FROM_FILE;
    private static String clusterName;

    static {
        try (Scanner sc = new Scanner(new FileInputStream(CLUSTERS_FILE))) {
            String line = sc.nextLine();
            clusterName = line.trim().split("\\s+")[0];
            if (line.contains("kerberosEnable=true")) {
                SECURITY_TYPE_FROM_FILE = KERBEROS;
            }
            else if (line.contains("secure=true")) {
                SECURITY_TYPE_FROM_FILE = MAPR_SASL;
            }
            else {
                SECURITY_TYPE_FROM_FILE = NONE;
            }
        } catch (FileNotFoundException e) {
            clusterName = "";
            SECURITY_TYPE_FROM_FILE = NONE;
        }
    }
    public static String getSecurityTypeFromClustersFile() {
        return SECURITY_TYPE_FROM_FILE;
    }
    public static String getClusterName() {
        return clusterName;
    }
    public static String findMapRHome() {
        String maprHome = System.getenv("MAPR_HOME");
        if (maprHome == null) {
            LOG.warn("Environment variable MAPR_HOME is null");
            maprHome = System.getProperty("mapr.home.dir");
            if (maprHome == null) {
                LOG.warn("System property mapr.home.dir is null");
                maprHome = SystemUtils.IS_OS_WINDOWS ? "C:/opt/mapr" : "/opt/mapr";
                LOG.warn("Setting MapR home as {} by default", maprHome);
            }
        }
        return maprHome;
    }

}
