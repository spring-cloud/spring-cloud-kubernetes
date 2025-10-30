#!/usr/bin/env bash
set -e

./mvnw deploy -DskipTests -B -Pfast,deploy ${@}
