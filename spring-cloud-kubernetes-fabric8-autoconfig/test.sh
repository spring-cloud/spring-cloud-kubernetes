#!/bin/bash

ADDR=(org.springframework.cloud.kubernetes.Fabric8PodUtilsTest org.springframework.cloud.kubernetes.Fabric8HealthIndicatorDisabledTest)
main() {
	for i in "${ADDR[@]}"; do
    	filename="${i}.txt"
        echo "searching for filename: ${filename}"
        file=$(find . -name "${filename}")
        echo "found file: ${file}"
  	done
}

main