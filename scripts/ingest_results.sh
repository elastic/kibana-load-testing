#!/bin/bash

cd kibana-load-testing || exit
mvn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B \
  exec:java -Dexec.mainClass=org.kibanaLoadTest.ingest.Main \
  -Dexec.classpathScope=test -Dexec.cleanupDaemonThreads=false
