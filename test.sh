#!/bin/bash

main() {

  echo 'start pulling common images'
  mkdir -p /tmp/docker/images
  result=$(find . -name 'current-images.txt')
  echo $result
  while read line; do
    docker pull "$line"
    replace_slash=$(tr '/' '-' <<< "$line")
    docker save "${line}" > "/tmp/docker/images/${replace_slash}.tar"
  done <$result

}

main