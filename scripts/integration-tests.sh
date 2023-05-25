#!/usr/bin/env bash
set -e

./mvnw clean install -B -Pdocs ${@}

