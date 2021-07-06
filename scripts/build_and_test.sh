#!/bin/bash

while getopts s: flag
do
    case "${flag}" in
        s) simulation=${OPTARG};;
        *) echo "incorrect argument, supported flags are: w, b, s"
    esac
done

cd kibana || exit
echo "Prepare environment"
./src/dev/ci_setup/setup.sh
echo "Build Kibana and run load scenario"
./test/scripts/jenkins_build_load_testing.sh -s "${simulation}"

cd ..
# zip puppeteer report
tar -czf puppeteer-report.tar.gz kibana-load-testing/puppeteer/output/**/*
# zip test report
tar -czf report.tar.gz kibana-load-testing/target/gatling/**/*
