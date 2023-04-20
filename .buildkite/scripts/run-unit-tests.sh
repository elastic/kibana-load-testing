#!/bin/bash

mvn -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dmockserver.logLevel=WARN '-Dtest=org.kibanaLoadTest.test.*Test' clean test