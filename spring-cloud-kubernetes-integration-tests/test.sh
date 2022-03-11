#!/bin/bash

ALL_INTEGRATION_PROJECTS=(
	"spring-cloud-kubernetes-client-config-it"
)

main() {
	run_tests "${ALL_INTEGRATION_PROJECTS[@]}"
}

MOVED_TO_TEST_CONTAINERS=(
	"spring-cloud-kubernetes-client-config-it"
)

run_tests() {
	arr=("$@")
	# cd ../spring-cloud-kubernetes-test-support
	# ${MVN} clean install
	# cd ../spring-cloud-kubernetes-integration-tests
	for p in "${arr[@]}"; do
		# echo "Running test: $p"
		# cd  $p
		# ${MVN} spring-boot:build-image \
		# 	-Dspring-boot.build-image.imageName=docker.io/springcloud/$p:${PROJECT_VERSION} -Dspring-boot.build-image.builder=paketobuildpacks/builder

		cd  $p
		mvn spring-boot:build-image \
			-Dspring-boot.build-image.imageName=docker.io/springcloud/$p:3.0.0-SNAPSHOT -Dspring-boot.build-image.builder=paketobuildpacks/builder
		if [[ "${MOVED_TO_TEST_CONTAINERS[*]}" =~ ${p} ]]; then
            docker save -o  "/tmp/images/${p}.tar" "springcloud/${p}"
		else
			echo "nothing"
		fi

		# "${KIND}" load docker-image docker.io/springcloud/$p:${PROJECT_VERSION}
		# # empty excludeITTests, so that integration tests will run
		# ${MVN} clean install -DexcludeITTests=
		# cd ..
	done
}

main