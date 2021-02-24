#!/bin/bash

cd kibana-load-testing || exit
mvn exec:java -Dexec.mainClass=org.kibanaLoadTest.ingest.Main -Dexec.classpathScope=test -Dexec.cleanupDaemonThreads=false