#!/bin/bash

SCRIPT_OUTPUT_DIR="kibana-load-testing/puppeteer/output/"
if [ -d "$SCRIPT_OUTPUT_DIR" ]; then
  echo "archive puppeteer output"
  tar -czf puppeteer-report.tar.gz kibana-load-testing/puppeteer/output/**/*
fi

GATLING_OUTPUT_DIR="kibana-load-testing/target/gatling/"
if [ -d "$GATLING_OUTPUT_DIR" ]; then
  echo "archive gatling test results"
  tar --exclude='*.log' -czf report.tar.gz kibana-load-testing/target/gatling/**/*
fi