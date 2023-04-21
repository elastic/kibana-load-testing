#!/bin/bash

export API_KEY="$(vault read -field=staging ci-shared/appex-qa/elastic_cloud/api_keys)"
mvn -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dmockserver.logLevel=WARN '-Dtest=org.kibanaLoadTest.test.integration.ElasticCloudTest' clean test
