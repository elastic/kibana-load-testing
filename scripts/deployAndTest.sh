#!/bin/bash

while getopts v:c:s: flag
do
    case "${flag}" in
        v) stackVersion=${OPTARG};;
        c) config=${OPTARG};;
        s) simulation=${OPTARG};;
    esac
done

echo "Running tests against Kibana cloud instance"
cd kibana-load-testing
mvn -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.test.failure.ignore=true -q clean compile
IFS=',' read -ra sim_array <<< "${simulation}"
for i in "${sim_array[@]}"
do
  echo "Running simulation $i ..."
  mvn gatling:test -q -DcloudStackVersion=stackVersion -DdeploymentConfig=config -Dgatling.simulationClass=org.kibanaLoadTest.simulation.$i
done