#!/bin/bash

source .buildkite/scripts/common/util.sh

pushd ../..

tar -xzvf apache-maven-3.8.5.tar.gz

export USER_FROM_VAULT="$(retry 5 5 vault read -field=username secret/kibana-issues/prod/coverage/elasticsearch)"
export PASS_FROM_VAULT="$(retry 5 5 vault read -field=password secret/kibana-issues/prod/coverage/elasticsearch)"
export HOST_FROM_VAULT="$(retry 5 5 vault read -field=host secret/kibana-issues/prod/coverage/elasticsearch)"
export DATA_STREAM_NAME=integration-test-gatling-data

./apache-maven-3.8.5/bin/mvn --no-transfer-progress -Dtest=DataStreamIngestionTest test
