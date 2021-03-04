#!/bin/bash

while getopts v:c:s: flag
do
    case "${flag}" in
        v) stackVersion=${OPTARG};;
        c) deployConfig=${OPTARG};;
        s) simulation=${OPTARG};;
        *) echo "incorrect argument, supported flags are: v, c, s"
    esac
done

echo "Running tests against Kibana cloud instance"
echo "stackVersion=${stackVersion}"
echo "deployConfig=${deployConfig}"
echo "simulation=${simulation}"

cd kibana-load-testing || exit
mvn -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.test.failure.ignore=true -q clean compile
mvn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B \
  exec:java -Dexec.mainClass=org.kibanaLoadTest.deploy.Main \
  -Dexec.classpathScope=test -Dexec.cleanupDaemonThreads=false \
  -DcloudStackVersion="${stackVersion}" \
  -DdeploymentConfig="${deployConfig}"
source target/cloudDeployment.txt
echo "deploymentId=${deploymentId}"

IFS=',' read -ra sim_array <<< "${simulation}"
for i in "${sim_array[@]}"
do
  echo "Running simulation $i ..."
  mvn gatling:test -q -DdeploymentId="${deploymentId}" -Dgatling.simulationClass=org.kibanaLoadTest.simulation.$i
  sleep 1m
done

# zip test report
cd ..
tar -czf report.tar.gz kibana-load-testing/target/gatling/**/*
cp  kibana-load-testing/target/lastDeployment.txt .
