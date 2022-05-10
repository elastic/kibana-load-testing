#!/bin/bash

echo "Env setup and build plugins"
./test/scripts/jenkins_xpack_build_plugins.sh

export KBN_NP_PLUGINS_BUILT=true

echo " -> Building and extracting default Kibana distributable for use in scalability testing"
node scripts/build --debug
linuxBuild="$(find "$KIBANA_DIR/target" -name 'kibana-*-linux-x86_64.tar.gz')"
installDir="$KIBANA_DIR/install/kibana"
mkdir -p "$installDir"
tar -xzf "$linuxBuild" -C "$installDir" --strip=1
mkdir -p "$WORKSPACE/kibana-build"
cp -pR install/kibana/. $WORKSPACE/kibana-build/

echo " -> Setup env for tests"
source test/scripts/jenkins_test_setup_xpack.sh
# back to $KIBANA_DIR
cd $KIBANA_DIR