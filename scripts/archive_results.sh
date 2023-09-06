#!/bin/bash

GATLING_OUTPUT_DIR="kibana-load-testing/target/gatling/"
if [ -d "$GATLING_OUTPUT_DIR" ]; then
  echo "archive gatling test results"
  tar --exclude='*.log' -czf report.tar.gz kibana-load-testing/target/gatling/**/*
fi