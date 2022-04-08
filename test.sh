#!/bin/bash

INTEGRATION_PROJECTS=(
            	"spring-cloud-kubernetes-client-config-it"
            )

            cd spring-cloud-kubernetes-integration-tests
            for p in "${INTEGRATION_PROJECTS[@]}"; do
              cd "${p}"
              mvn spring-boot:build-image \
              	  -Dspring-boot.build-image.imageName=docker.io/springcloud/$p:${PROJECT_VERSION} \
                  -Dspring-boot.build-image.builder=paketobuildpacks/builder
              mvn clean verify -DexcludeITTests=
              cd ..
            done