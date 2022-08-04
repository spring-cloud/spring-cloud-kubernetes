#!/bin/bash

main() {


if [[ $CURRENT_INDEX -eq $(($NUMBER_OF_JOBS-1)) ]]; then
   echo 'last one'
else
            while [ ! ${TESTCONTAINERS_REUSE_ENABLE_1} == "true" ]; do
            	echo $TESTCONTAINERS_REUSE_ENABLE_1
              echo 'fabric8 istio test not yet finished, will wait for 5 seconds'
              sleep 5
            done
fi

echo ${!TESTCONTAINERS_REUSE_ENABLE_1}         

}

main