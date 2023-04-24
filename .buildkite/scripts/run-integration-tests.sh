#!/bin/bash

API_KEY="$(vault kv get -field=staging kv/ci-shared/appex-qa/elastic_cloud/api_keys)"
if [ -z "${API_KEY}" ]
then
  echo "Failed to read secrets, skipping test run"
  exit 1
else
  export API_KEY
  mvn -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dmockserver.logLevel=WARN '-Dtest=org.kibanaLoadTest.test.integration.ElasticCloudTest' clean test
fi
