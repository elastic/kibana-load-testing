#!/bin/bash

while getopts s: flag
do
    case "${flag}" in
        s) simulations=${OPTARG};;
        *) echo "incorrect argument, supported flags are: w, b, s"
    esac
done

echo "Simulation classes: $simulations";

cd kibana-load-testing || exit

n=0
until [ "$n" -ge 3 ]
do
   ./scripts/build_kibana_and_plugins.sh && break  # retry up to 3 times
   n=$((n+1))
   sleep 15
done

pushd ../kibana-load-testing
echo " -> Building puppeteer project"
cd puppeteer
yarn install && yarn build
popd

export ELASTIC_APM_ACTIVE=true

echo " -> Running gatling load testing"
IFS=',' read -ra sim_array <<< "${simulations}"
for i in "${sim_array[@]}"; do
  echo "Running sudo /usr/local/sbin/drop-caches";
  sudo /usr/local/sbin/drop-caches
  export GATLING_SIMULATIONS="$i"
  node scripts/functional_tests \
    --kibana-install-dir "$KIBANA_INSTALL_DIR" \
    --config x-pack/test/load/config.ts;
done

echo " -> Simulations run is finished"

