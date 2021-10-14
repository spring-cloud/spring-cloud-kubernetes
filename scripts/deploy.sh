#!/usr/bin/env bash
set -e

./mvnw deploy -DskipTests -B -Pfast,deploy ${@}
./mvnw dockerfile:push -pl :spring-cloud-kubernetes-configuration-watcher -Pdockerpush ${@}
./mvnw dockerfile:push -pl :spring-cloud-kubernetes-discoveryserver -Pdockerpush ${@}
./mvnw dockerfile:push -pl :spring-cloud-kubernetes-configserver -Pdockerpush ${@}
