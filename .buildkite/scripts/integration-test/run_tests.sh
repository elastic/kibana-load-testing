#!/bin/bash

GCS_ARTIFACTS_REL="gcs_artifacts"
GCS_ARTIFACTS_DIR="${WORKSPACE}/${GCS_ARTIFACTS_REL}"
GCS_BUCKET="gs://kibana-performance/scalability-tests"
BUILD_HASH="0186d545-4947-44a7-a508-a25e87275d17"

mkdir -p "${GCS_ARTIFACTS_DIR}"
gsutil cp -r "$GCS_BUCKET/$BUILD_HASH" "${GCS_ARTIFACTS_DIR}/"