#!/bin/bash

# standard bash error handling
set -o errexit;
set -o pipefail;
set -o nounset;
# debug commands
set -x;

# working dir to install binaries etc, cleaned up on exit
BIN_DIR="$(mktemp -d)"
# kind binary will be here
KIND="${BIN_DIR}/kind"

ISTIOCTL="${BIN_DIR}/istio-1.6.2/bin/istioctl"

CURRENT_DIR="$(pwd)"

MVN="${CURRENT_DIR}/../mvnw"

MVN_VERSION=$($MVN -q \
    -Dexec.executable=echo \
    -Dexec.args='${project.version}' \
    --non-recursive \
    exec:exec)

ALL_INTEGRATION_PROJECTS=(
	"spring-cloud-kubernetes-core-k8s-client-it"
	"spring-cloud-kubernetes-client-config-it"
	"spring-cloud-kubernetes-configuration-watcher-it"
	"spring-cloud-kubernetes-client-loadbalancer-it"
	"spring-cloud-kubernetes-client-reactive-discovery-client-it"
)
INTEGRATION_PROJECTS=(${INTEGRATION_PROJECTS:-${ALL_INTEGRATION_PROJECTS[@]}})

DEFAULT_PULLING_IMAGES=(
	"jettech/kube-webhook-certgen:v1.2.2"
	"rabbitmq:3-management"
	"zookeeper:3.6.2"
	"rodolpheche/wiremock:2.27.2"
	"wurstmeister/kafka:2.13-2.6.0"
)
PULLING_IMAGES=(${PULLING_IMAGES:-${DEFAULT_PULLING_IMAGES[@]}})

LOADING_IMAGES=(${LOADING_IMAGES:-${DEFAULT_PULLING_IMAGES[@]}} "docker.io/springcloud/spring-cloud-kubernetes-configuration-watcher:${MVN_VERSION}")
# cleanup on exit (useful for running locally)
cleanup() {
    "${KIND}" delete cluster || true
    rm -rf "${BIN_DIR}"
}
trap cleanup EXIT

# util to install the latest kind version into ${BIN_DIR}
install_latest_kind() {
    # clone kind into a tempdir within BIN_DIR
    local tmp_dir
    tmp_dir="$(TMPDIR="${BIN_DIR}" mktemp -d "${BIN_DIR}/kind-source.XXXXX")"
    cd "${tmp_dir}" || exit
    git clone https://github.com/kubernetes-sigs/kind && cd ./kind
    make install INSTALL_DIR="${BIN_DIR}"
}

# util to install a released kind version into ${BIN_DIR}
install_kind_release() {
    VERSION="v0.11.1"
    KIND_BINARY_URL="https://github.com/kubernetes-sigs/kind/releases/download/${VERSION}/kind-linux-amd64"
    if [[ "$OSTYPE" == "darwin"*  ]]; then
        KIND_BINARY_URL="https://github.com/kubernetes-sigs/kind/releases/download/${VERSION}/kind-darwin-amd64"
	elif [[ "$OSTYPE" == "cygwin" ]]; then
        KIND_BINARY_URL="https://github.com/kubernetes-sigs/kind/releases/download/${VERSION}/kind-windows-amd64"
	elif [[ "$OSTYPE" == "msys" ]]; then
        KIND_BINARY_URL="https://github.com/kubernetes-sigs/kind/releases/download/${VERSION}/kind-windows-amd64"
	elif [[ "$OSTYPE" == "win32" ]]; then
        KIND_BINARY_URL="https://github.com/kubernetes-sigs/kind/releases/download/${VERSION}/kind-windows-amd64"
	else
        echo "Unknown OS, using linux binary"
	fi
    wget -O "${KIND}" "${KIND_BINARY_URL}"
    chmod +x "${KIND}"
}

main() {
    # get kind
    install_kind_release

    # create a cluster
    cd $CURRENT_DIR

    #TODO what happens if cluster is already there????
    "${KIND}" create cluster --config=kind-config.yaml -v 2147483647

    # set KUBECONFIG to point to the cluster
    kubectl cluster-info --context kind-kind

	#setup nginx ingress
	# pulling necessary images for setting up the integration test environment
	for i in "${PULLING_IMAGES[@]}"; do
		echo "Pull images for prepping testing environment: $i"
		docker pull $i
	done
	for i in "${LOADING_IMAGES[@]}"; do
		echo "Loading images into Kind: $i"
		"${KIND}" load docker-image $i
	done
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
    sleep 5 # hold 5 sec so that the pods can be created
    kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=420s
	
	

    # This creates the service account, role, and role binding necessary for Spring Cloud k8s apps
	kubectl apply -f ./permissions.yaml

	# cd ${BIN_DIR}
	# curl -L https://istio.io/downloadIstio | sh -
	#"${ISTIOCTL}" install --set profile=demo

	# running tests..
	if [[ $CIRCLECI ]]; then
		#This splits projects across all circleci instances, it returns a list of projects separated by a space
		SPLIT_PROJECTS=$(printf "%s\n" "${INTEGRATION_PROJECTS[@]}" | circleci tests split)
		echo "split tests $SPLIT_PROJECTS"
		#This splits the projects back into an array so we can iterate over them
		IFS='\n' read -ra PROJECTS <<< "$SPLIT_PROJECTS"
		echo "projects $PROJECTS"
		run_tests "${PROJECTS[@]}"
	else
		run_tests "${INTEGRATION_PROJECTS[@]}"
	fi

    # teardown will happen automatically on exit
}

run_tests() {
	arr=("$@")
	for p in "${arr[@]}"; do
		echo "Running test: $p"
		cd  $p
		${MVN} spring-boot:build-image \
			-Dspring-boot.build-image.imageName=docker.io/springcloud/$p:${MVN_VERSION} -Dspring-boot.build-image.builder=paketobuildpacks/builder
		"${KIND}" load docker-image docker.io/springcloud/$p:${MVN_VERSION}
		${MVN} clean install -P it
		cd ..
	done
}

main
