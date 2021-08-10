#!/bin/bash

while getopts v:c:s:n: flag
do
    case "${flag}" in
        v) stackVersion=${OPTARG};;
        c) deployConfig=${OPTARG};;
        s) simulation=${OPTARG};;
        n) test_runs_number=${OPTARG};;
        *) echo "incorrect argument, supported flags are: v, c, s"
    esac
done

echo "##### Running with arguments: #####"
echo "stackVersion=${stackVersion} deployConfig=${deployConfig} simulation=${simulation} test_runs_number=${test_runs_number}"

cd kibana-load-testing

echo "##### Install dependencies #####"
mvn --no-transfer-progress -Dmaven.wagon.http.retryHandler.count=3 -q install -DskipTests
echo "##### Compile source code #####"
mvn --no-transfer-progress scala:testCompile

IFS=',' read -ra version_array <<< "${stackVersion}"
IFS=',' read -ra sim_array <<< "${simulation}"

for version in "${version_array[@]}"; do
  echo "##### Create a new ${version} Elastic Stack deployment #####"
  mvn --no-transfer-progress exec:java -Dexec.mainClass=org.kibanaLoadTest.deploy.Create \
    -Dexec.classpathScope=test -Dexec.cleanupDaemonThreads=false \
    -DcloudStackVersion="${version}" \
    -DdeploymentConfig="${deployConfig}"
  # check deployment file and apply env variables
  if [ ! -f target/cloudDeployment.txt ]; then
	  echo "!!!Deployment failed!!!"
	  continue
  fi
  source target/cloudDeployment.txt
#  echo "##### Validating scenario #####"
#  yarn build && node build/index.js
  echo "##### Running tests against Kibana cloud instance ${deploymentId} #####"
  for i in $(seq "$test_runs_number"); do
    for j in "${sim_array[@]}"; do
      echo "Running simulation $j $i-time..."
      mvn gatling:test -q -DdeploymentId="${deploymentId}" -Dgatling.simulationClass=org.kibanaLoadTest.simulation.$j
      # wait a minute between scenarios
      sleep 1m
    done
  done

  echo "##### Delete deployment ${deploymentId} #####"
  mvn --no-transfer-progress exec:java -Dexec.mainClass=org.kibanaLoadTest.deploy.Delete \
    -Dexec.classpathScope=test -Dexec.cleanupDaemonThreads=false \
    -DdeploymentId="${deploymentId}"
done

echo "##### Zip test report #####"
cd .. && tar -czf report.tar.gz kibana-load-testing/target/gatling/**/*
