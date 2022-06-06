#!/usr/bin/env bash

retry() {
  local retries=$1
  shift
  local delay=$1
  shift
  local attempts=1

  until "$@"; do # Until exit status is 0
    retry_exit_status=$?
    echo "Exited with $retry_exit_status" >&2
    if ((retries == "0")); then
      return $retry_exit_status
    elif ((attempts == retries)); then
      echo "Failed $attempts retries" >&2
      return $retry_exit_status
    else
      echo "Retrying $((retries - attempts)) more times..." >&2
      attempts=$((attempts + 1))
      sleep "$delay"
    fi
  done
}
