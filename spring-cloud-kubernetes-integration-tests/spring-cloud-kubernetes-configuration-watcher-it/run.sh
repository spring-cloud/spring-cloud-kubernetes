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

# cleanup on exit (useful for running locally)
cleanup() {
    "${KIND}" delete cluster || true
    rm -rf "${BIN_DIR}"

    docker kill kind-registry
	docker rm kind-registry
	docker network rm kind
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
    VERSION="v0.5.1"
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
        echo "Uknown OS, using linux binary"
	fi
    wget -O "${KIND}" "${KIND_BINARY_URL}"
    chmod +x "${KIND}"
}

setup_registry() {
set -o errexit

# create registry container unless it already exists
reg_name='kind-registry'
reg_port='5000'
running="$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)"
if [ "${running}" != 'true' ]; then
  docker run \
    -d --restart=always -p "${reg_port}:5000" --name "${reg_name}" \
    registry:2
fi

# create a cluster with the local registry enabled in containerd
cat <<EOF | "${KIND}" create cluster --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${reg_port}"]
    endpoint = ["http://${reg_name}:${reg_port}"]
EOF

# connect the registry to the cluster network
docker network connect "kind" "${reg_name}"

# tell https://tilt.dev to use the registry
# https://docs.tilt.dev/choosing_clusters.html#discovering-the-registry
for node in $("${KIND}" get nodes); do
  kubectl annotate node "${node}" "kind.x-k8s.io/registry=localhost:${reg_port}";
done

}

main() {
    # get kind
    install_latest_kind

    # create a cluster
    cd $CURRENT_DIR

    # create registry container unless it already exists
	reg_name='kind-registry'
	reg_port='5000'
	running="$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)"
	if [ "${running}" != 'true' ]; then
  		docker run \
    	-d --restart=always -p "${reg_port}:5000" --name "${reg_name}" \
    	registry:2
	fi

    #TODO what happens if cluster is already there????
    "${KIND}" create cluster --config kind-config.yaml --loglevel=debug

    # connect the registry to the cluster network
	docker network connect "kind" "${reg_name}"

	# tell https://tilt.dev to use the registry
	# https://docs.tilt.dev/choosing_clusters.html#discovering-the-registry
	for node in $("${KIND}" get nodes); do
	  kubectl annotate node "${node}" "kind.x-k8s.io/registry=localhost:${reg_port}";
	done

    # set KUBECONFIG to point to the cluster
    kubectl cluster-info --context kind-kind

	#setup nginx ingress
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/static/provider/kind/deploy.yaml
    kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=360s

    # This creates the service account, role, and role binding necessary for Spring Cloud k8s apps
	kubectl apply -f ./permissions.yaml

#	cd ${BIN_DIR}
#	curl -L https://istio.io/downloadIstio | sh -
	#"${ISTIOCTL}" install --set profile=demo

	cd $CURRENT_DIR

    # TODO: invoke your tests here
    ../../mvnw clean install -P it
    # teardown will happen automatically on exit
}

main
