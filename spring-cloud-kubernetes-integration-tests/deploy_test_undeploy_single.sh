#!/usr/bin/env bash

set -e

# The script assumes that there is only one application deployed by FMP running at a time

SCRIPT_ABSOLUTE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
mvnCmd="${SCRIPT_ABSOLUTE_DIR}"/../mvnw
kubectlCmd=/snap/bin/microk8s.kubectl

export KUBECONFIG=/tmp/kubeconfig

echo "Starting application deployment to cluster"
$mvnCmd clean package fabric8:build fabric8:push fabric8:deploy -Pfmp
echo "Finished deployment"

echo "Waiting for pod to become Ready"
# We are leveraging the fact the fact that FMP adds the group label and the fact that only one
# application can be running at a time
$kubectlCmd wait --for=condition=Ready pod -l group=org.springframework.cloud --timeout=60s > /dev/null

echo "Starting the integration tests"
$mvnCmd verify -Pfmp,it -Dfabric8.skip

echo "Successfully executed integration tests"
echo "Undeploying application"
$mvnCmd fabric8:undeploy -Pfmp
