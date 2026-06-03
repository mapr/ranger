%undefine __check_files

summary:    HPE DataFabric Ecosystem Pack: Apache Ranger
license:    Hewlett Packard Enterprise, CopyRight
Vendor:     Hewlett Packard Enterprise
name:       mapr-ranger-hive-plugin
version:    __RELEASE_VERSION__
release:    1
prefix:     /
group:      HPE
buildarch:  noarch
AutoReqProv: no

%description
Apache Ranger (Hive Plugin) distribution included in HPE DataFabric Software Ecosystem Pack
Tag: __RELEASE_BRANCH__
Commit: __GIT_COMMIT__

%clean
echo "NOOP"

%files
__PREFIX__/ranger
__PREFIX__/roles

# role package for hive plugin
%pre

# CONF_BACKUP - save old configs in OLD_TIMESTAMP/conf directory (any upgrade)
# CONF_OVERWRITE - overwrite new configs with old ones (ONLY if first 3 digits HAVE NOT been changed)

if [ "$1" = "2" ]; then
    OLD_TIMESTAMP=$(rpm -qi mapr-ranger-hive-plugin | awk -F': ' '/Version/ {print $2}')
    OLD_VERSION=$( echo ${OLD_TIMESTAMP} | cut -d'.' -f1-3 )

    # ====== CONF_BACKUP start ======

    mkdir -p __PREFIX__/ranger/ranger-${OLD_TIMESTAMP}/conf/ranger-hive-plugin
    cp __PREFIX__/ranger/ranger-${OLD_VERSION}/ranger-hive-plugin/install.properties __PREFIX__/ranger/ranger-${OLD_TIMESTAMP}/conf/ranger-hive-plugin

    # ====== CONF_BACKUP end ======

    # ====== CONF_OVERWRITE start ======

    # Comparing first 3 digits
    if [ "__VERSION_3DIGIT__" == "${OLD_VERSION}" ] ; then
	mkdir -p __PREFIX__/ranger/ranger-${OLD_VERSION}/.conf.backup/hive
	cp __PREFIX__/ranger/ranger-${OLD_VERSION}/ranger-hive-plugin/install.properties __PREFIX__/ranger/ranger-${OLD_VERSION}/.conf.backup/hive
    fi

    # ====== CONF_OVERWRITE end ======

    if [ "__VERSION_3DIGIT__" != "${OLD_VERSION}" ] ; then
        if [ -f __PREFIX__/conf/conf.d/warden.rangerhiveplugin.conf ]; then
            rm -Rf __PREFIX__/conf/conf.d/warden.rangerhiveplugin.conf
        fi
    fi
fi

%post
# Overwrite version file with our one
echo "__VERSION_3DIGIT__" >  __PREFIX__/ranger/rangerversion

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

# ====== CONF_OVERWRITE start ======

if [ "$1" = "2" ]; then
    TIMESTAMP=$(rpm -qi mapr-ranger-hive-plugin | awk -F': ' '/Version/ {print $2}')
    VERSION_FROM_TIMESTAMP=$( echo $TIMESTAMP | cut -d'.' -f1-3 )
    if [ -d __PREFIX__/ranger/ranger-${VERSION_FROM_TIMESTAMP}/.conf.backup/hive ] ; then
	cp __PREFIX__/ranger/ranger-${VERSION_FROM_TIMESTAMP}/.conf.backup/hive/install.properties __PREFIX__/ranger/ranger-${VERSION_FROM_TIMESTAMP}/ranger-hive-plugin/
    fi
    rm -rf __PREFIX__/ranger/ranger-${VERSION_FROM_TIMESTAMP}/.conf.backup/hive
fi

# ====== CONF_OVERWRITE end ======

%postun
if [ "$1" = "0" ]; then
    if [ -f __PREFIX__/conf/conf.d/warden.rangerhiveplugin.conf ]; then
        rm -f __PREFIX__/conf/conf.d/warden.rangerhiveplugin.conf
    fi
    rm -Rf __PREFIX__/ranger/ranger-__VERSION_3DIGIT__/ranger-hive-plugin
    if [ ! -f __PREFIX__/roles/ranger-usersync ] && [ ! -f __PREFIX__/roles/ranger ] && [ ! -f __PREFIX__/roles/ranger-yarn-plugin ] && [ ! -f __PREFIX__/roles/ranger-hdfs-plugin ] && [ ! -f __PREFIX__/roles/ranger-prestodb-plugin ]; then
        rm -Rf __PREFIX__/ranger
        rm -Rf /etc/ranger
    fi
fi

NEW_VERSION=$(cat __PREFIX__/ranger/rangerversion 2>/dev/null)

# If 3-digit version was changed
if [ -n "$NEW_VERSION" -a "__VERSION_3DIGIT__" != "$NEW_VERSION" ]; then
  # Remove old ranger-hive-plugin directory anyway
  rm -Rf __PREFIX__/ranger/ranger-__VERSION_3DIGIT__/ranger-hive-plugin
  # If we are the last one here and version is changed, remove the whole old 3-digit directory
  if ! ls __PREFIX__/ranger/ranger-__VERSION_3DIGIT__/ranger-* >/dev/null 2>&1 ; then
    rm -Rf __PREFIX__/ranger/ranger-__VERSION_3DIGIT__
  fi
fi
