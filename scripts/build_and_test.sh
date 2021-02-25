#!/bin/bash

while getopts w:b:s: flag
do
    case "${flag}" in
        w) workspace=${OPTARG};;
        b) kibanaBranch=${OPTARG};;
        s) simulation=${OPTARG};;
        *) echo "incorrect argument, supported flags are: w, b, s"
    esac
done

cd kibana || exit
echo "Prepare environment"
./src/dev/ci_setup/setup.sh
echo "Build Kibana and run load scenario"
./test/scripts/jenkins_build_load_testing.sh -s "${simulation}"