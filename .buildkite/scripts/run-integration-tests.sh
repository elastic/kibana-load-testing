#!/bin/bash

export API_KEY="$(retry 5 5 vault read -field=value secret/kibana-issues/dev/cloud-staging-api-key)"
mvn -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dmockserver.logLevel=WARN '-Dtest=org.kibanaLoadTest.test.integration.ElasticCloudTest' clean test



