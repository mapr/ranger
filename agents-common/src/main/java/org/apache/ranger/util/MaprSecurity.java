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

import com.mapr.baseutils.cldbutils.CLDBRpcCommonUtils;
import com.mapr.fs.ShimLoader;
import com.mapr.security.JNISecurity;

public class MaprSecurity {
    public static final String SECURITY_TYPE_PROPERTY = "ranger.security.type";
    public static final String MAPR_SASL = "maprsasl";
    public static final String KERBEROS = "kerberos";
    public static final String NONE = "none";
    private static final String clusterName;

    static {
        ShimLoader.load();

        clusterName = CLDBRpcCommonUtils.getInstance().getCurrentClusterName();
    }
    public static String getNativeSecurityType() {
        if (!JNISecurity.IsSecurityEnabled(clusterName)) {
            return NONE;
        }
        else if (JNISecurity.IsKerberosEnabled(clusterName)) {
            return KERBEROS;
        }
       return MAPR_SASL;
    }
    public static String getClusterName() {
        return clusterName;
    }
}
