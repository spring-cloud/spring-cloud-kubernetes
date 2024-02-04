#!/usr/bin/env bash
set -e

rm ~/.testcontainers.properties
echo 'testcontainers.reuse.enable=true' > ~/.testcontainers.properties

./mvnw clean install -B -Pdocs ${@}

rm ~/.testcontainers.properties
docker kill $(docker ps -q)
