#!/usr/bin/env bash
set -e

./mvnw deploy -DskipTests -B -Pfast,deploy {{systemProps}} ${@}
./mvnw dockerfile:push -pl :spring-cloud-kubernetes-configuration-watcher -Pdockerpush ${@}
