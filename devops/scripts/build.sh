#!/bin/bash
set -ex

SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
. "${SCRIPT_DIR}/_initialize_package_variables.sh"
. "${SCRIPT_DIR}/_utils.sh"

build_ranger() {
  mvn ${RANGER_MVN_ARGS}

  mkdir -p "${BUILD_ROOT}/build"

  tar xvf target/ranger-*-admin.tar.gz -C "${BUILD_ROOT}/build"
  tar xvf target/ranger-*-usersync.tar.gz -C "${BUILD_ROOT}/build"
  tar xvf target/ranger-*-hive-plugin.tar.gz -C "${BUILD_ROOT}/build"
  tar xvf target/ranger-*-yarn-plugin.tar.gz -C "${BUILD_ROOT}/build"

  # RAN-260's shared_package_files adaptation
  admin_dir="$(find ${BUILD_ROOT}/build -type d -name 'ranger*admin')"
  usersync_dir="$(find ${BUILD_ROOT}/build -type d -name 'ranger*usersync')"
  mkdir "${admin_dir}/shared_package_files" "${usersync_dir}/shared_package_files"
  cp -r bin "${admin_dir}/shared_package_files"
  cp -r bin "${usersync_dir}/shared_package_files"
}

main() {
  echo "Cleaning '${BUILD_ROOT}' dir..."
  rm -rf "$BUILD_ROOT"

  if [ "$DO_DEPLOY" = "true" ] && [ "$OS" = "redhat" ]; then
    echo "Deploy is enabled"
    export RANGER_MVN_ARGS="${RANGER_MVN_ARGS} -DaltDeploymentRepository=${REPOSITORY_ID}::default::${MAPR_MAVEN_REPO} deploy"
  fi

  echo "Building project..."
  build_ranger

  echo "Preparing directory structure..."
  setup_role "mapr-ranger"
  setup_role "mapr-ranger-usersync"
  setup_role "mapr-ranger-hive-plugin"
  setup_role "mapr-ranger-yarn-plugin"

  setup_package "mapr-ranger" "admin"
  setup_package "mapr-ranger-usersync" "usersync"
  setup_package "mapr-ranger-hive-plugin" "hive-plugin"
  setup_package "mapr-ranger-yarn-plugin" "yarn-plugin"

  echo "Building packages..."
  build_package "mapr-ranger"
  build_package "mapr-ranger-usersync"
  build_package "mapr-ranger-hive-plugin"
  build_package "mapr-ranger-yarn-plugin"

  echo "Resulting packages:"
  find "$DIST_DIR" -exec readlink -f {} \;
}

main
