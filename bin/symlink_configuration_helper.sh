#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Global vars
MAPR_HOME="${BASEMAPR:-/opt/mapr}"
RANGER_VERSION_FILE="$MAPR_HOME"/ranger/rangerversion
RANGER_VERSION=$(cat "$RANGER_VERSION_FILE")
HADOOP_VERSION_FILE="$MAPR_HOME"/hadoop/hadoopversion
HADOOP_VERSION=$(cat "$HADOOP_VERSION_FILE")

# logging
log() {
  local prefix="$(date +%Y-%m-%d\ %H:%M:%S,%3N) "
  echo "${prefix} $@"
}

# link following jars from core lib to ranger-usersync lib.
# this is required for MaprSecurityLoginModule.login()
# 1. maprfs jars
# 2. protobuf
# 3. jackson
# 4. bc fips jars
# 5. hadoop-shaded-guava
# if already linked, then refresh them
link_mapr_core_lib_for_usersync() {
  local MAPR_CORE_LIB="$MAPR_HOME"/lib
  local RANGER_USERSYNC_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-usersync/lib
  local HADOOP_COMMON_LIB="$MAPR_HOME"/hadoop/hadoop-"$HADOOP_VERSION"/share/hadoop/common/lib

  # if needed add more jars
  local MAPRFS_JARS="$MAPR_CORE_LIB"/maprfs-*
  local PROTOBUF_JARS="$MAPR_CORE_LIB"/protobuf-java-*
  local JACKSON_CORE_JAR="$MAPR_CORE_LIB"/jackson-core-2.*
  local BC_FIPS_JAR="$MAPR_CORE_LIB"/bc-fips-*
  local BCTLS_FIPS_JAR="$MAPR_CORE_LIB"/bctls-fips-*
  local HADOOP_SHADED_GUAVA_JAR="$HADOOP_COMMON_LIB"/hadoop-shaded-guava-*
  local MAPR_WEB_SECURITY_JAR="$MAPR_CORE_LIB"/mapr-security-web-*
  local JETTY_UTIL_JAR="$HADOOP_COMMON_LIB"/jetty-util-*

  # if already exists, unlink first. In case applying patch, we need to remove old links
  find $RANGER_USERSYNC_LIB -type l -name "maprfs-*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "protobuf-java-*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "jackson-core-2.*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "bc-fips-*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "bctls-fips-*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "hadoop-shaded-guava-*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "mapr-security-web-*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "jetty-util-*" -delete

  # create the links again
  ln -sf $MAPRFS_JARS $RANGER_USERSYNC_LIB
  ln -sf $PROTOBUF_JARS $RANGER_USERSYNC_LIB
  ln -sf $JACKSON_CORE_JAR $RANGER_USERSYNC_LIB
  ln -sf $BC_FIPS_JAR $RANGER_USERSYNC_LIB
  ln -sf $BCTLS_FIPS_JAR $RANGER_USERSYNC_LIB
  ln -sf $HADOOP_SHADED_GUAVA_JAR $RANGER_USERSYNC_LIB
  ln -sf $MAPR_WEB_SECURITY_JAR $RANGER_USERSYNC_LIB
  ln -sf $JETTY_UTIL_JAR $RANGER_USERSYNC_LIB
}

# link following jars from core lib to ranger-usersync lib.
# this is required for MaprSecurityLoginModule.login()
# 1. maprfs jars
# 2. protobuf
# 3. javax.servlet-api
# if already linked, then refresh them
link_mapr_core_lib_for_admin() {
  local MAPR_CORE_LIB="$MAPR_HOME"/lib
  local RANGER_ADMIN_EWS_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin/ews/lib
  local RANGER_ADMIN_WEBAPP_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin/ews/webapp/WEB-INF/lib

  # if needed add more jars
  local MAPRFS_JARS="$MAPR_CORE_LIB"/maprfs-*
  local PROTOBUF_JARS="$MAPR_CORE_LIB"/protobuf-java-*
  local SERVLET_JARS="$RANGER_ADMIN_WEBAPP_LIB"/javax.servlet-api-*
  local MAPR_WEB_SECURITY_JAR="$MAPR_CORE_LIB"/mapr-security-web-*

  # if already exists, unlink first. In case applying patch, we need to remove old links
  find $RANGER_ADMIN_EWS_LIB -type l -name "maprfs-*" -delete
  find $RANGER_ADMIN_EWS_LIB -type l -name "protobuf-java-*" -delete
  find $RANGER_ADMIN_EWS_LIB -type l -name "javax.servlet-api-*" -delete
  find $RANGER_ADMIN_WEBAPP_LIB -type l -name "mapr-security-web-*" -delete

  # create the links again
  ln -sf $MAPRFS_JARS $RANGER_ADMIN_EWS_LIB
  ln -sf $PROTOBUF_JARS $RANGER_ADMIN_EWS_LIB
  ln -sf $SERVLET_JARS $RANGER_ADMIN_EWS_LIB
  ln -sf $MAPR_WEB_SECURITY_JAR $RANGER_ADMIN_WEBAPP_LIB
}

# if HBase exists and hbase plugin is installed;
# to be able to communicate with HBase client, HBase's jars needs to be linked to webapp lib
configure_hbase_jars_for_admin() {
  # if the links exist, remove them first anyway
  local RANGER_ADMIN_WEBAPP_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin/ews/webapp/WEB-INF/lib
  find $RANGER_ADMIN_WEBAPP_LIB -type l -name "hbase-*" -delete

  # checking role file for hbase
  if [ -f "${MAPR_HOME}"/roles/hbase ]; then
    # find the jars
    local HBASE_VERSION_FILE="$MAPR_HOME"/hbase/hbaseversion
    local HBASE_VERSION=$(cat "$HBASE_VERSION_FILE")
    local HBASE_LIB="$MAPR_HOME"/hbase/hbase-"$HBASE_VERSION"/lib
    local HBASE_JARS="$HBASE_LIB"/hbase-*
    # link the jars
    ln -sf $HBASE_JARS $RANGER_ADMIN_WEBAPP_LIB
  fi
}

# link following jars to RANGER_HOME/ranger-admin/cred/lib
# stax2-api and woodstox-core from RANGER_ADMIN_WEBAPP_LIB
# bc-fips and bctls-fips from RANGER_ADMIN_WEBAPP_LIB lib
# jackson-core-2, maprfs and protobuf-java-3 from mapr core lib
# hadoop-shaded-guava from hadoop common lib
configure_cred_lib_for_admin() {
  # libraries
  local MAPR_CORE_LIB="$MAPR_HOME"/lib
  local RANGER_ADMIN_CRED_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin/cred/lib
  local RANGER_ADMIN_JISQL_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin/jisql/lib
  local RANGER_ADMIN_WEBAPP_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin/ews/webapp/WEB-INF/lib
  local HADOOP_COMMON_LIB="$MAPR_HOME"/hadoop/hadoop-"$HADOOP_VERSION"/share/hadoop/common/lib

  # needed jars
  local MAPRFS_JARS="$MAPR_CORE_LIB"/maprfs-*
  local PROTOBUF_JARS="$MAPR_CORE_LIB"/protobuf-java-*
  local JACKSON_CORE_JAR="$RANGER_ADMIN_WEBAPP_LIB"/jackson-core-2.*
  local BC_FIPS_JAR="$RANGER_ADMIN_WEBAPP_LIB"/bc-fips-*
  local BCTLS_FIPS_JAR="$RANGER_ADMIN_WEBAPP_LIB"/bctls-fips-*
  local HADOOP_SHADED_GUAVA_JAR="$HADOOP_COMMON_LIB"/hadoop-shaded-guava-*
  local STAX2_API_JAR="$RANGER_ADMIN_WEBAPP_LIB"/stax2-api-*
  local WOODSTOX_CORE_JAR="$RANGER_ADMIN_WEBAPP_LIB"/woodstox-core-*

  # if already exists, unlink first. In case applying patch, we need to remove old links
  find $RANGER_ADMIN_CRED_LIB -type l -name "maprfs-*" -delete
  find $RANGER_ADMIN_CRED_LIB -type l -name "protobuf-java-*" -delete
  find $RANGER_ADMIN_CRED_LIB -type l -name "jackson-core-2.*" -delete
  find $RANGER_ADMIN_CRED_LIB -type l -name "bc-fips-*" -delete
  find $RANGER_ADMIN_CRED_LIB -type l -name "bctls-fips-*" -delete
  find $RANGER_ADMIN_CRED_LIB -type l -name "hadoop-shaded-guava-*" -delete
  find $RANGER_ADMIN_CRED_LIB -type l -name "stax2-api-*" -delete
  find $RANGER_ADMIN_CRED_LIB -type l -name "woodstox-core-*" -delete

  find $RANGER_ADMIN_JISQL_LIB -type l -name "bc-fips-*" -delete
  find $RANGER_ADMIN_JISQL_LIB -type l -name "bctls-fips-*" -delete

  # create the links again
  ln -sf $MAPRFS_JARS $RANGER_ADMIN_CRED_LIB
  ln -sf $PROTOBUF_JARS $RANGER_ADMIN_CRED_LIB
  ln -sf $JACKSON_CORE_JAR $RANGER_ADMIN_CRED_LIB
  ln -sf $BC_FIPS_JAR $RANGER_ADMIN_CRED_LIB
  ln -sf $BCTLS_FIPS_JAR $RANGER_ADMIN_CRED_LIB
  ln -sf $HADOOP_SHADED_GUAVA_JAR $RANGER_ADMIN_CRED_LIB
  ln -sf $STAX2_API_JAR $RANGER_ADMIN_CRED_LIB
  ln -sf $WOODSTOX_CORE_JAR $RANGER_ADMIN_CRED_LIB

  ln -sf $BC_FIPS_JAR $RANGER_ADMIN_JISQL_LIB
  ln -sf $BCTLS_FIPS_JAR $RANGER_ADMIN_JISQL_LIB
}
