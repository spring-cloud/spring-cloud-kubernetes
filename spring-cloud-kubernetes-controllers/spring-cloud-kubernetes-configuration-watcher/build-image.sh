#!/usr/bin/env bash
export IMAGE_TAG="2.0.0-SNAPSHOT.internal.10"
docker build -t gcr.io/staging-161313/spring-cloud-kubernetes-configuration-watcher:${IMAGE_TAG} .
docker push gcr.io/staging-161313/spring-cloud-kubernetes-configuration-watcher:${IMAGE_TAG}
