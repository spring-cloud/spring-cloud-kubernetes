#!/bin/bash

main() {
	TAG=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
        cd spring-cloud-kubernetes-controllers
        while read integ_test; do
          #docker save -o /tmp/docker/images/${integ_test}.tar docker.io/springcloud/${integ_test}:$TAG
          echo $integ_test
        done < <( $(mvn -Dexec.executable='echo' -Dexec.args='${project.artifactId}' exec:exec -q | grep -v 'spring-cloud-kubernetes-controllers') )
        cd ..
}

main