#!/bin/bash

SCRIPT_OUTPUT_DIR="kibana-load-testing/puppeteer/output/"
if [ -d "$SCRIPT_OUTPUT_DIR" ]; then
  echo "archive puppeteer output"
  tar -czf puppeteer-report.tar.gz "$SCRIPT_OUTPUT_DIR/**/*"
fi

GATLING_OUTPUT_DIR="kibana-load-testing/target/gatling/"
if [ -d "$GATLING_OUTPUT_DIR" ]; then
  echo "archive gatling test results"
  tar -czf report.tar.gz "$GATLING_OUTPUT_DIR/**/*"
fi