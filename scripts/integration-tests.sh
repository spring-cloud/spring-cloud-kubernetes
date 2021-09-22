#!/usr/bin/env bash
set -e

./mvnw clean install -B -Pdocs ${@}
cd spring-cloud-kubernetes-integration-tests
./run.sh ${@}

