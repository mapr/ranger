#!/usr/bin/env bash
#set -x

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

MAPR_HOME="${BASEMAPR:-/opt/mapr}"

###############################################
#    LOGGING RELATED SECTION                  #
###############################################

source "${MAPR_HOME}"/server/common-ecosystem.sh 2>/dev/null
MAPR_ENABLE_LOGS="${MAPR_ENABLE_LOGS:-false}"
configure_logging(){
{ set +x; } 2>/dev/null
if [ "${MAPR_ENABLE_LOGS}" == "true" ]; then
  set -x;
fi
}

configure_logging
if [ $? -ne 0 ]; then
  echo "MAPR_HOME seems to not be set correctly or mapr-core not installed"
  exit 1
fi

###############################################
#    GLOBAL VARIABLES                         #
###############################################

isAdminInstalled=false
if [ -f "${MAPR_HOME}"/roles/ranger ]; then isAdminInstalled=true ; fi
isUsersyncInstalled=false
if [ -f "${MAPR_HOME}"/roles/ranger-usersync ]; then isUsersyncInstalled=true ; fi

logInfo "Ranger: Starting Ranger configuration."
MAPR_CLUSTERS_CONF="$MAPR_HOME"/conf/mapr-clusters.conf
MAPR_ENABLE_LOGS="${MAPR_ENABLE_LOGS:-false}"
RANGER_VERSION_FILE="$MAPR_HOME"/ranger/rangerversion
RANGER_VERSION=$(cat "$RANGER_VERSION_FILE")
RANGER_HOME="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"
RANGER_CONF_DIR="$RANGER_HOME"/conf

# ranger-admin
if $isAdminInstalled; then
  RANGER_ADMIN_HOME="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-admin
  # ranger-admin conf is created by its setup.sh, if it does not exist, print a warning and exit
  if [ -d "$RANGER_ADMIN_HOME"/ews/webapp/WEB-INF/classes/conf ]; then
    RANGER_ADMIN_CONF_DIR="$RANGER_ADMIN_HOME"/ews/webapp/WEB-INF/classes/conf
  else
    echo "Warning: Ranger is not configured, please check the logs in ${MAPR_HOME}/logs/configure.log."
    logWarn "Ranger: Ranger is not configured! Could not found ${RANGER_ADMIN_HOME}/ews/webapp/WEB-INF/classes/conf directory."
    logWarn "Ranger: Please make sure that install.properties file for Ranger-Admin is ready and its setup.sh file is executed."
    exit 1
  fi
  RANGER_ADMIN_SITE="$RANGER_ADMIN_CONF_DIR"/ranger-admin-site.xml
  RANGER_ADMIN_WARDEN_NAME="warden.ranger-admin.conf"
fi

# ranger-usersync
if $isUsersyncInstalled; then
  RANGER_USERSYNC_HOME="$MAPR_HOME"/ranger/ranger-"$RANGER_VERSION"/ranger-usersync
  # ranger-usersync conf is created by its setup.sh, if it does not exist, add warn logs and skip it
  if [ -d "$RANGER_USERSYNC_HOME"/conf ]; then
    RANGER_USERSYNC_CONF_DIR="$RANGER_USERSYNC_HOME"/conf
  else
    echo "Warning: Ranger is not configured, please check the logs in ${MAPR_HOME}/logs/configure.log."
    logWarn "Ranger: Ranger is not configured! Could not found ${RANGER_USERSYNC_HOME}/conf directory."
    logWarn "Ranger: Please make sure that install.properties file for Ranger-Usersync is ready and its setup.sh file is executed."
    exit 1
  fi
  RANGER_USERSYNC_SITE="$RANGER_USERSYNC_CONF_DIR"/ranger-ugsync-site.xml
  RANGER_USERSYNC_WARDEN_NAME="warden.ranger-usersync.conf"
fi

# Get MAPR_USER and MAPR_GROUP
DAEMON_CONF="${MAPR_HOME}/conf/daemon.conf"
MAPR_USER=${MAPR_USER:-$( [ -f "$DAEMON_CONF" ] && awk -F = '$1 == "mapr.daemon.user" { print $2 }' "$DAEMON_CONF" )}
MAPR_USER=${MAPR_USER:-"mapr"}
export MAPR_USER
MAPR_GROUP=${MAPR_GROUP:-$( [ -f "$DAEMON_CONF" ] && awk -F = '$1 == "mapr.daemon.group" { print $2 }' "$DAEMON_CONF" )}
MAPR_GROUP=${MAPR_GROUP:-"$MAPR_USER"}
export MAPR_GROUP
logInfo "Ranger: Initial variables are set."

###############################################
#    METHODS FOR XML FILES                    #
###############################################

remove_property() {
  local property_name=$1
  local site_file=$2
  sed -i '/<property>/{:a;N;/<\/property>/!ba; /<name>'"${property_name}"'<\/name>/d}' "${site_file}"
}

add_property() {
  local property_name=$1
  local property_value=$2
  local site_file=$3
  sed -i -e "s|</configuration>|  <property>\n    <name>${property_name}</name>\n    <value>${property_value}</value>\n  </property>\n</configuration>|" "${site_file}"
}

set_property() {
  local property_name="<name>"$1"<\/name>"
  local property_value=$2
  local site_file=$3
  if ! grep -q "${property_name}" "${site_file}" ; then
    add_property "${property_name}" "${property_value}" "${site_file}"
  else
    sed -i '/'"${property_name}"'/{:a;N;/<\/value>/!ba; s|<value>.*</value>|<value>'"${property_value}"'</value>|}' "${site_file}"
  fi
}

get_property_value() {
  local property_name="<name>"$1"<\/name>"
  local site_file=$2
  sed -n '/'"${property_name}"'/{:a;N;/<\/value>/!ba {s|.*<value>\(.*\)</value>|\1|p}}' "${site_file}"
}

###############################################
#    DIR/FILE RELATED                         #
###############################################

is_ranger_not_configured_yet(){
  if [ -f "$RANGER_CONF_DIR"/.not_configured_yet ]; then
    return 0; # 0 = true
  else
    return 1;
  fi
}

is_admin_not_configured_yet(){
  if [ -f "$RANGER_CONF_DIR"/.not_configured_yet_admin ]; then
    return 0; # 0 = true
  else
    return 1;
  fi
}

is_usersync_not_configured_yet(){
  if [ -f "$RANGER_CONF_DIR"/.not_configured_yet_usersync ]; then
    return 0; # 0 = true
  else
    return 1;
  fi
}

remove_fresh_install_indicator(){
  if is_ranger_not_configured_yet ; then
    logInfo "Ranger: Removing ${RANGER_CONF_DIR}/.not_configured_yet file."
    rm -f "${RANGER_CONF_DIR}"/.not_configured_yet
  fi
  rm -f "${RANGER_CONF_DIR}"/.not_configured_yet_usersync
  rm -f "${RANGER_CONF_DIR}"/.not_configured_yet_admin
}

change_file_dir_owners_permissions(){
  for each in $(find "$MAPR_HOME"/ranger ! -type l)
  do
    chown "${MAPR_USER}":"${MAPR_GROUP}" "$each" 2>/dev/null
  done
  chmod +x "${RANGER_HOME}"/bin/* 2>/dev/null
}

###############################################
#    WARDEN RELATED                           #
###############################################

add_refresh_warden_files(){
  local MAPR_WARDEN_LOCATION="$MAPR_HOME"/conf/conf.d

  if [ ! -e "$MAPR_WARDEN_LOCATION" ]; then
    mkdir -p "$MAPR_WARDEN_LOCATION" >/dev/null 2>&1
  fi

  if [ -f "$MAPR_WARDEN_LOCATION"/"$RANGER_ADMIN_WARDEN_NAME" ]; then
    rm -f "$MAPR_WARDEN_LOCATION"/"$RANGER_ADMIN_WARDEN_NAME"
  fi
  if [ -f "$MAPR_WARDEN_LOCATION"/"$RANGER_USERSYNC_WARDEN_NAME" ]; then
    rm -f "$MAPR_WARDEN_LOCATION"/"$RANGER_USERSYNC_WARDEN_NAME"
  fi

  if $isAdminInstalled; then
    cp "$RANGER_CONF_DIR"/"$RANGER_ADMIN_WARDEN_NAME" "$MAPR_WARDEN_LOCATION"
  fi
  if $isUsersyncInstalled; then
    cp "$RANGER_CONF_DIR"/"$RANGER_USERSYNC_WARDEN_NAME" "$MAPR_WARDEN_LOCATION"
  fi
}

create_admin_restart_file(){
  mkdir -p "${MAPR_HOME}/conf/restart"
  if $isAdminInstalled; then
      cat > "${MAPR_HOME}/conf/restart/ranger-admin-${RANGER_VERSION}.restart" <<EOF
#!/bin/bash
if [ -z "${MAPR_TICKETFILE_LOCATION}" ] && [ -e "${MAPR_HOME}/conf/mapruserticket" ]; then
    export MAPR_TICKETFILE_LOCATION="${MAPR_HOME}/conf/mapruserticket"
fi
maprcli node services -action restart -name ranger-admin -nodes $(hostname) &>/dev/null
EOF
      chmod +x "${MAPR_HOME}/conf/restart/ranger-admin-${RANGER_VERSION}.restart"
      chown -R "$MAPR_USER":"$MAPR_GROUP" "${MAPR_HOME}/conf/restart/ranger-admin-${RANGER_VERSION}.restart"
  fi
}

create_usersync_restart_file(){
  mkdir -p "${MAPR_HOME}/conf/restart"
  if $isUsersyncInstalled; then
      cat > "${MAPR_HOME}/conf/restart/ranger-usersync-${RANGER_VERSION}.restart" <<EOF
#!/bin/bash
if [ -z "${MAPR_TICKETFILE_LOCATION}" ] && [ -e "${MAPR_HOME}/conf/mapruserticket" ]; then
    export MAPR_TICKETFILE_LOCATION="${MAPR_HOME}/conf/mapruserticket"
fi
maprcli node services -action restart -name ranger-usersync -nodes $(hostname) &>/dev/null
EOF
      chmod +x "${MAPR_HOME}/conf/restart/ranger-usersync-${RANGER_VERSION}.restart"
      chown -R "$MAPR_USER":"$MAPR_GROUP" "${MAPR_HOME}/conf/restart/ranger-usersync-${RANGER_VERSION}.restart"
  fi
}

###############################################
#    REFRESH SYMLINKS                         #
###############################################
# in below script, there are functions for refreshing symlink
# call below functions in main part
# link_mapr_core_lib_for_admin, configure_hbase_jars_for_admin and link_mapr_core_lib_for_usersync
source "$RANGER_HOME"/bin/symlink_configuration_helper.sh

###############################################
#    SECURITY RELATED                         #
###############################################

is_secure_cluster(){
  local isSecure=$(head -n 1 "${MAPR_CLUSTERS_CONF}" | grep secure= | sed 's/^.*secure=//' | sed 's/ .*$//')

  if [ "${isSecure}" = "true" ]; then
    return 0 # true
  else
    return 1
  fi
}

configure_ssl(){
  local property_ssl_enabled="ranger.service.https.attrib.ssl.enabled"
  local property_admin_url="ranger.usersync.policymanager.baseURL"

  if $isAdminInstalled; then
    local http_port=$(get_property_value "ranger.service.http.port" "${RANGER_ADMIN_SITE}")
    local https_port=$(get_property_value "ranger.service.https.port" "${RANGER_ADMIN_SITE}")

    if is_secure_cluster ; then
      logInfo "Ranger: Cluster security is enabled, SSL will be configured."
      set_property ${property_ssl_enabled} "true" "${RANGER_ADMIN_SITE}"

      if $isUsersyncInstalled; then
        if [ -z "${https_port}" ]; then
          https_port="6182"
        fi
        logInfo "Ranger: Setting usersync's policy manager port as ${https_port}."
        set_property ${property_admin_url} "https://$(hostname):${https_port}" "${RANGER_USERSYNC_SITE}"
      fi
    else
      logInfo "Ranger: Cluster security is not enabled, SSL will not be configured."
      set_property ${property_ssl_enabled} "false" "${RANGER_ADMIN_SITE}"

      if $isUsersyncInstalled; then
        if [ -z "${http_port}" ]; then
          http_port="6080"
        fi
        logInfo "Ranger: Setting usersync's policy manager port as ${http_port}."
        set_property ${property_admin_url} "http://$(hostname):${http_port}" "${RANGER_USERSYNC_SITE}"
      fi
    fi
  fi
}

###############################################
#    DEFAULT BEHAVIOUR                        #
###############################################

enable_periodic_user_sync(){
  local property_name="ranger.usersync.enabled"
  if is_usersync_not_configured_yet ; then
    logInfo "Ranger: Enabling Ranger's user synchronization."
    set_property ${property_name} "true" "${RANGER_USERSYNC_SITE}"
  fi
}

###############################################
#    MAIN SECTION                             #
###############################################

# file/dir permissions
if is_ranger_not_configured_yet ; then
  logInfo "Ranger: First configuration."
  logInfo "Ranger: Changing file/dir permissions."
  change_file_dir_owners_permissions
else
  logInfo "Ranger: Not first configuration."
fi

# default
if $isUsersyncInstalled; then
  enable_periodic_user_sync
fi

# symlinks
logInfo "Ranger: Creating/refreshing symlinks in ranger-admin libraries."
if $isAdminInstalled; then
  link_mapr_core_lib_for_admin
  configure_cred_lib_for_admin
  configure_hbase_jars_for_admin
fi
logInfo "Ranger: Creating/refreshing symlinks in ranger-usersync library."
if $isUsersyncInstalled; then
  link_mapr_core_lib_for_usersync
fi

# security
logInfo "Ranger: Configuring SSL."
configure_ssl

# warden
logInfo "Ranger: Creating/refreshing warden files."
add_refresh_warden_files

# restart Admin
if ! is_admin_not_configured_yet ; then
  logInfo "Ranger: Creating Admin restart file."
  create_admin_restart_file
fi

# restart UserSync
if ! is_usersync_not_configured_yet ; then
  logInfo "Ranger: Creating UserSync restart file."
  create_usersync_restart_file
fi

# finalizing
remove_fresh_install_indicator
logInfo "Ranger: Finished Ranger configuration."
