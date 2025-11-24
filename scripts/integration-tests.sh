#!/usr/bin/env bash
set -e

if [ -f "~/.testcontainers.properties" ]; then
    rm ~/.testcontainers.properties
fi
echo 'testcontainers.reuse.enable=true' > ~/.testcontainers.properties

#./mvnw clean install -B -Pdocs ${@}
./mvnw clean install -DskipITs -DskipTests -B -Pdocs ${@}

rm ~/.testcontainers.properties
# docker kill $(docker ps -q)
