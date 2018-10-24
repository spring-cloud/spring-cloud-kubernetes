#!/usr/bin/env bash

set -e

# The script launches and tests each test application - one at a time

for D in $(find . -maxdepth 1 -mindepth 1 -not -path '*/\.*'  -type d); do
  pushd ${D} > /dev/null
  ../deploy_test_undeploy_single.sh
  /snap/bin/microk8s.kubectl wait --for=delete pod -l group=org.springframework.cloud --timeout=60s > /dev/null
  popd > /dev/null
done

