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
# if already linked, then refresh them
link_mapr_core_lib_for_usersync() {
  local MAPR_CORE_LIB="$MAPR_HOME"/lib
  local RANGER_USERSYNC_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-usersync/lib
  local RANGER_ADMIN_WEBAPP_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin/ews/webapp/WEB-INF/lib

  # if needed add more jars
  local MAPRFS_JARS="$MAPR_CORE_LIB"/maprfs-*
  local PROTOBUF_JARS="$MAPR_CORE_LIB"/protobuf-java-*
  local JACKSON_CORE_JAR="$RANGER_ADMIN_WEBAPP_LIB"/jackson-core-2.*

  # if already exists, unlink first. In case applying patch, we need to remove old links
  find $RANGER_USERSYNC_LIB -type l -name "maprfs-*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "protobuf-java-*" -delete
  find $RANGER_USERSYNC_LIB -type l -name "jackson-core-2.*" -delete

  # create the links again
  ln -sf $MAPRFS_JARS $RANGER_USERSYNC_LIB
  ln -sf $PROTOBUF_JARS $RANGER_USERSYNC_LIB
  ln -sf $JACKSON_CORE_JAR $RANGER_USERSYNC_LIB
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
  local SERVLET_JARS="$MAPR_CORE_LIB"/javax.servlet-api-*

  # if already exists, unlink first. In case applying patch, we need to remove old links
  find $RANGER_ADMIN_EWS_LIB -type l -name "maprfs-*" -delete
  find $RANGER_ADMIN_EWS_LIB -type l -name "protobuf-java-*" -delete
  find $RANGER_ADMIN_EWS_LIB -type l -name "javax.servlet-api-*" -delete
  # for webapp, we need only maprfs jars, rest already exists
  find $RANGER_ADMIN_WEBAPP_LIB -type l -name "maprfs-*" -delete

  # create the links again
  ln -sf $MAPRFS_JARS $RANGER_ADMIN_EWS_LIB
  ln -sf $PROTOBUF_JARS $RANGER_ADMIN_EWS_LIB
  ln -sf $SERVLET_JARS $RANGER_ADMIN_EWS_LIB
  # webapp too
  ln -sf $MAPRFS_JARS $RANGER_ADMIN_WEBAPP_LIB
}

# if HBase exists and hbase plugin is installed;
# to be able to communicate with HBase client, HBase's jars needs to be linked to webapp lib
configure_hbase_jars_for_admin() {
  # if the links exist, remove them first anyway
  local RANGER_ADMIN_WEBAPP_LIB="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin/ews/webapp/WEB-INF/lib
  find $RANGER_ADMIN_WEBAPP_LIB -type l -name "hbase-*" -delete

  # checking role files
  if [ -f "${MAPR_HOME}"/roles/hbase ] && [ -f "${MAPR_HOME}"/roles/ranger-hbase-plugin ]; then
    # find the jars
    local HBASE_VERSION_FILE="$MAPR_HOME"/hbase/hbaseversion
    local HBASE_VERSION=$(cat "$HBASE_VERSION_FILE")
    local HBASE_LIB="$MAPR_HOME"/hbase/hbase-"$HBASE_VERSION"/lib
    local HBASE_JARS="$HBASE_LIB"/hbase-*
    # link the jars
    ln -sf $HBASE_JARS $RANGER_ADMIN_WEBAPP_LIB
  fi
}
