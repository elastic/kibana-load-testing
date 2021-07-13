#!/bin/bash

SCRIPT_OUTPUT_DIR="kibana-load-testing/puppeteer/output/"
if [ -d "$PUPPETEER_DIR" ]; then
  # Take action if $PUPPETEER_DIR exists. #
  echo "archive puppeteer output"
  tar -czf puppeteer-report.tar.gz kibana-load-testing/puppeteer/output/**/*
fi

GATLING_OUTPUT_DIR="kibana-load-testing/target/gatling/"
if [ -d "$GATLING_OUTPUT_DIR" ]; then
  # Take action if $$GATLING_OUTPUT_DIR exists. #
  echo "archive gatling test results"
  tar -czf report.tar.gz kibana-load-testing/target/gatling/**/*
fi