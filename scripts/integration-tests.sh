#!/usr/bin/env bash
set -e

if [ -f "~/.testcontainers.properties" ]; then
    rm ~/.testcontainers.properties
fi
echo 'testcontainers.reuse.enable=true' > ~/.testcontainers.properties

#./mvnw clean install -B -Pdocs ${@}
./mvnw clean install -DskipITs -DskipTests -Dspring-boot.build-image.skip=true -B -Pdocs ${@}

rm ~/.testcontainers.properties
containers=$(docker ps -q)
if [ -n "$containers" ]; then
  docker kill $containers
fi
