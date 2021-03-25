#!/bin/bash

while getopts v:c:s: flag
do
    case "${flag}" in
        v) stackVersion=${OPTARG};;
        c) deployConfig=${OPTARG};;
        s) simulation=${OPTARG};;
        n) test_runs_count=${OPTARG};;
        *) echo "incorrect argument, supported flags are: v, c, s"
    esac
done

echo "Running tests against Kibana cloud instance"
echo "stackVersion=${stackVersion}"
echo "deployConfig=${deployConfig}"
echo "simulation=${simulation}"
echo "test_runs_count=${test_runs_count}"

cd kibana-load-testing || exit

echo "install dependencies"
mvn --no-transfer-progress -Dmaven.wagon.http.retryHandler.count=3 -q install -DskipTests
echo "compile source code"
mvn --no-transfer-progress scala:testCompile
echo "create deployment"
mvn exec:java -Dexec.mainClass=org.kibanaLoadTest.deploy.Create \
  -Dexec.classpathScope=test -Dexec.cleanupDaemonThreads=false \
  -DcloudStackVersion="${stackVersion}" \
  -DdeploymentConfig="${deployConfig}" || exit
source target/cloudDeployment.txt
echo "deploymentId=${deploymentId}"

IFS=',' read -ra sim_array <<< "${simulation}"
for i in $(seq "$test_runs_count"); do
  for j in "${sim_array[@]}"; do
    echo "Running simulation $i ..."
    mvn gatling:test -q -DdeploymentId="${deploymentId}" -Dgatling.simulationClass=org.kibanaLoadTest.simulation.$j
    # wait a minute between scenarios
    sleep 1m
  done
done

echo "delete deployment"
mvn exec:java -Dexec.mainClass=org.kibanaLoadTest.deploy.Delete \
  -Dexec.classpathScope=test -Dexec.cleanupDaemonThreads=false \
  -DdeploymentId="${deploymentId}" || exit

# zip test report
cd ..
tar -czf report.tar.gz kibana-load-testing/target/gatling/**/*
cp  kibana-load-testing/target/lastDeployment.txt .
