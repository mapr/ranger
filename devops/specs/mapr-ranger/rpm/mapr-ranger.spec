%define _binaries_in_noarch_packages_terminate_build   0
%undefine __check_files

summary:     HPE DataFabric Ecosystem Pack: Apache Ranger
license:     Hewlett Packard Enterprise, CopyRight
Vendor:      Hewlett Packard Enterprise
name:        mapr-ranger
version:     __RELEASE_VERSION__
release:     1
prefix:      /
group:       HPE
buildarch:   noarch
requires:    mapr-client >= 7.1.0, mapr-hadoop-client >= 3.3.4
conflicts:   mapr-core < 7.1.0
AutoReqProv: no

%description
Apache Ranger (Admin) distribution included in HPE DataFabric Software Ecosystem Pack
Tag: __RELEASE_BRANCH__
Commit: __GIT_COMMIT__

%clean
echo "NOOP"

%files
__PREFIX__/ranger
__PREFIX__/roles

%pre
# $1 -eq 1 install
# $1 -eq 2 upgrade
# N/A     uninstall
# saving old configuration
[ -n "$VERBOSE" ] && echo "pre install called with argument \`$1'" >&2
[ -n "$VERBOSE" ] && set -x ;

# CONF_BACKUP - save old configs in OLD_TIMESTAMP/conf directory (any upgrade)
# CONF_OVERWRITE - overwrite new configs with old ones (ONLY if first 3 digits HAVE NOT been changed)

if [ "$1" = "2" ]; then

    OLD_TIMESTAMP=$(rpm -qi mapr-ranger | awk -F': ' '/Version/ {print $2}')
    OLD_VERSION=$(echo ${OLD_TIMESTAMP} | cut -d'.' -f1-3)

    # ====== CONF_BACKUP start ======

    mkdir -p __PREFIX__/ranger/ranger-${OLD_TIMESTAMP}/conf/ranger-admin
    if [ -f __PREFIX__/ranger/ranger-${OLD_VERSION}/conf/warden.ranger-admin.conf ]; then
        cp __PREFIX__/ranger/ranger-${OLD_VERSION}/conf/warden.ranger-admin.conf __PREFIX__/ranger/ranger-${OLD_TIMESTAMP}/conf/ranger-admin
    fi

    cp __PREFIX__/ranger/ranger-${OLD_VERSION}/ranger-admin/install.properties __PREFIX__/ranger/ranger-${OLD_TIMESTAMP}/conf/ranger-admin
    if [ -f __PREFIX__/ranger/ranger-${OLD_VERSION}/ranger-admin/ews/webapp/WEB-INF/classes/conf/ranger-admin-site.xml ]; then
        cp __PREFIX__/ranger/ranger-${OLD_VERSION}/ranger-admin/ews/webapp/WEB-INF/classes/conf/ranger-admin-site.xml __PREFIX__/ranger/ranger-${OLD_TIMESTAMP}/conf/ranger-admin
    fi

    DAEMON_CONF=__PREFIX__/conf/daemon.conf
    if [ -f "$DAEMON_CONF" ]; then
        MAPR_USER=$( awk -F = '$1 == "mapr.daemon.user" { print $2 }' $DAEMON_CONF)
        MAPR_GROUP=$( awk -F = '$1 == "mapr.daemon.group" { print $2 }' $DAEMON_CONF)
        if [ ! -z "$MAPR_USER" ]; then
            chown -R ${MAPR_USER}:${MAPR_GROUP} __PREFIX__/ranger/ranger-${OLD_TIMESTAMP}
        fi
    fi

    # ====== CONF_BACKUP end ======

    # ====== CONF_OVERWRITE start ======

    # Comparing first 3 digits
    if [ "__VERSION_3DIGIT__" == "$OLD_VERSION" ]; then
	mkdir -p "__INSTALL_3DIGIT__"/.conf.backup/admin
	cp __PREFIX__/ranger/ranger-${OLD_VERSION}/ranger-admin/install.properties "__INSTALL_3DIGIT__"/.conf.backup/admin
	if [ -f __PREFIX__/ranger/ranger-${OLD_VERSION}/ranger-admin/ews/webapp/WEB-INF/classes/conf/ranger-admin-site.xml ]; then
	    cp __PREFIX__/ranger/ranger-${OLD_VERSION}/ranger-admin/ews/webapp/WEB-INF/classes/conf/ranger-admin-site.xml "__INSTALL_3DIGIT__"/.conf.backup/admin
	fi
    fi

    # ====== CONF_OVERWRITE end ======
fi


%post
# for initial insatallation, change dir owner
# for update keep install.properties file as it is
[ -n "$VERBOSE" ] && echo "postinst called with argument \`$1'" >&2
[ -n "$VERBOSE" ] && set -x

# Overwrite version file and shared bin/ directory with our ones
echo "__VERSION_3DIGIT__" >  __PREFIX__/ranger/rangerversion
cp -rp __INSTALL_3DIGIT__/ranger-admin/shared_package_files/bin __INSTALL_3DIGIT__

# Always become unconfigured for configure.sh after upgrade or install
touch "__INSTALL_3DIGIT__/conf/.not_configured_yet"
touch "__INSTALL_3DIGIT__/conf/.not_configured_yet_admin"

if [ "$1" = "1" ]; then
  if [ ! -d /etc/hadoop ]; then
    mkdir /etc/hadoop
  fi
fi

# ====== CONF_OVERWRITE start ======

if [ "$1" = "2" -a -d "__INSTALL_3DIGIT__"/.conf.backup/admin ]; then
  cp "__INSTALL_3DIGIT__"/.conf.backup/admin/install.properties __INSTALL_3DIGIT__/ranger-admin/
  if [ -f "__INSTALL_3DIGIT__"/.conf.backup/admin/ranger-admin-site.xml ]; then
    cp "__INSTALL_3DIGIT__"/.conf.backup/admin/ranger-admin-site.xml __INSTALL_3DIGIT__/ranger-admin/ews/webapp/WEB-INF/classes/conf/
  fi
  rm -rf "__INSTALL_3DIGIT__"/.conf.backup/admin
fi

# ====== CONF_OVERWRITE end ======

chmod -R 775 "__INSTALL_3DIGIT__"/bin

DAEMON_CONF=__PREFIX__/conf/daemon.conf
if [ -f "$DAEMON_CONF" ]; then
  MAPR_USER=$( awk -F = '$1 == "mapr.daemon.user" { print $2 }' $DAEMON_CONF)
  MAPR_GROUP=$( awk -F = '$1 == "mapr.daemon.group" { print $2 }' $DAEMON_CONF)
  if [ ! -z "$MAPR_USER" ]; then
    for each in $(find __PREFIX__/ranger ! -type l)
    do
        chown "${MAPR_USER}":"${MAPR_GROUP}" "$each" 2>/dev/null
    done
  fi
fi

  if [ -f __PREFIX__/roles/ranger-usersync ]; then
    cat > "__PREFIX__/roles/ranger-usersync" <<EOF
PKG_HOME_DIR=__PREFIX__/ranger/ranger-__VERSION_3DIGIT__
PKG_CONFIG_COMMAND=
EOF
  fi

%preun
#Stopping core services

if [ -e __PREFIX__/conf/conf.d/warden.ranger-admin.conf ]; then
    rm __PREFIX__/conf/conf.d/warden.ranger-admin.conf
fi

if [ -z "${MAPR_TICKETFILE_LOCATION}" ] && [ -e "__PREFIX__/conf/mapruserticket" ]; then
    export MAPR_TICKETFILE_LOCATION="__PREFIX__/conf/mapruserticket"
fi

echo "Stopping Ranger Admin service."
RESULT=$(__INSTALL_3DIGIT__/ranger-admin/ews/ranger-admin-services.sh stop)
STATUS=$?
if [ $STATUS -ne 0 ] ; then
    echo "$RESULT"
fi

%postun
# Remove ranger directory if it is uninstall or if version changed and we do not need old directory
VERSION_FILE_CONTENT=$(cat __PREFIX__/ranger/rangerversion 2>/dev/null)
if [ -n "$VERSION_FILE_CONTENT" -a "__VERSION_3DIGIT__" != "$VERSION_FILE_CONTENT" ]; then
  rm -Rf __INSTALL_3DIGIT__/ranger-admin
  # If we are the last one here and version is changed, remove the whole old 3-digit directory
  if ! ls __INSTALL_3DIGIT__/ranger-* >/dev/null 2>&1 ; then
    rm -Rf __INSTALL_3DIGIT__
  fi
fi

if [ "$1" = "0" ]; then
  rm -f /usr/bin/ranger-admin
  ls /etc/ranger 2>/dev/null | grep -xv "usersync" | xargs rm -rf 2>/dev/null
  rm -Rf __PREFIX__/ranger/ranger-__VERSION_3DIGIT__/ranger-admin
  if [ -e __PREFIX__/pid/rangeradmin.pid ]; then
    rm __PREFIX__/pid/rangeradmin.pid
  fi
  if [ ! -f __PREFIX__/roles/ranger-usersync ] && [ ! -f __PREFIX__/roles/ranger-hive-plugin ] && [ ! -f __PREFIX__/roles/ranger-yarn-plugin ] && [ ! -f __PREFIX__/roles/ranger-hdfs-plugin ] && [ ! -f __PREFIX__/roles/ranger-prestodb-plugin ]; then
    rm -Rf __PREFIX__/ranger
    rm -Rf /etc/ranger
  fi

  if [ -f __PREFIX__/roles/ranger-usersync ]; then
    cat > "__PREFIX__/roles/ranger-usersync" <<EOF
PKG_HOME_DIR=__PREFIX__/ranger/ranger-__VERSION_3DIGIT__
EOF
  fi
fi
