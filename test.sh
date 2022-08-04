#!/bin/bash

main() {


if [[ $CURRENT_INDEX -eq $(($NUMBER_OF_JOBS-1)) ]]; then
	cd spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-istio-it/
    ../.././mvnw clean install -Dskip.build.image=true
    cd ../..

    echo "testcontainers.reuse.enable=true" > ~/.testcontainers.properties

	return
fi

TEST_CONTAINERS_REUSE_SUPPORT_FILE=~/.testcontainers.properties2
while [ ! -f "$TEST_CONTAINERS_REUSE_SUPPORT_FILE" ]; do
	echo 'fabric8 istio test not yet finished, will wait for 5 seconds'
    sleep 5
done




          # - find all tests
          # - exclude Fabric8IstionIT
          # - only take classes that have @Test inside them
          # - ignore the ones that have 'abstract class'. we do this because otherwise we would pass
          #   to -DtestsToRun an abstract class, and it will not run anything.
          # - drop the "begining" xxx/src/test/java
          # - replace / with .
          # - drop last ".java"
          # - replace newline with space
          # - replace '\n' with ' '

          PLAIN_TEST_CLASSNAMES=($(find . -name '*.java' \
                		| grep 'src/test/java' \
                    	| grep -v 'Fabric8IstioIT' \
                    	| xargs grep -l '@Test' \
                        | xargs grep -L 'abstract class' \
                    	| sed 's/.*src.test.java.//g' \
                    	| sed 's@/@.@g' \
                    	| sed 's/.\{5\}$//' \
                    	| tr '\n' ' '))

          # classes that have @Test and are abstract, for example: "LabeledSecretWithPrefixTests"
          # - exclude Fabric8IstionIT
          # - only take classes that have @Test inside them
          # - only take classes that are abstract
          # - drop everything up until the last "/"
          # - drop ".java"

          ABSTRACT_TEST_CLASSNAMES_COMMAND="find . -name '*.java' \
                        | grep  'src/test/java' \
                        | grep -v 'Fabric8IstioIT' \
                        | xargs grep -l '@Test' \
                        | xargs grep -l 'abstract class' \
                        | sed 's/.*\///g' \
                        | sed 's/.java//g'"

          # find classes that extend abstract test classes
          DERIVED_FROM_ABSTRACT_CLASSES_COMMAND="find . -name '*.java' \
                        | grep  'src/test/java' \
                        | grep -v 'Fabric8IstioIT' \
                        | xargs grep -l 'extends replace_me ' \
                        | sed 's/.*src.test.java.//g' \
                    	| sed 's@/@.@g' \
                    	| sed 's/.\{5\}$//' \
                    	| tr '\n' ' '"

          while read class_name; do
              	replaced=$(echo ${DERIVED_FROM_ABSTRACT_CLASSES_COMMAND/replace_me/"$class_name"})
            	result=($(eval $replaced))
            	PLAIN_TEST_CLASSNAMES+=(${result[@]})
          done < <(eval $ABSTRACT_TEST_CLASSNAMES_COMMAND)

          IFS=$'\n'
          SORTED_TEST_CLASSNAMES=( $(sort <<< "${PLAIN_TEST_CLASSNAMES[*]} | uniq -u") )
          unset IFS

          number_of_tests=${#SORTED_TEST_CLASSNAMES[@]}
          number_of_jobs=${NUMBER_OF_JOBS}
          current_index=${CURRENT_INDEX}

          # we do a "-1" here, because the last job will run Fabrci8IstioIT, thus start the k3s
          # container and also set echo "testcontainers.reuse.enable=true" > ~/.testcontainers.properties.
          # all other jobs will wait for this one to finish.
          per_instance=$((number_of_tests / number_of_jobs - 1))

          # we do not get an ideal distribution all the time, so this is needed to add one more test
          # to the first "reminder" number of instances.

          # consider the case when there are 10 tests, and 4 instances
          # 10/4=2 (and this is "per_instance"), at the same time "reminder" = (10 - 4 * 2) = 2
          # this means that the first instance will run (2 + 1) tests
          # second instance will run (2 + 1) tests
          # all subsequent instances will run 2 tests.

          reminder=$((number_of_tests - number_of_jobs * per_instance))
          elements_in_current_instance=$((per_instance + 1))

          left_bound=0
          right_bound=0

          # we are in a range where we might need to add one more test to each instance
          # notice the "less then" condition here, it is important and must not change
          if [[ $current_index -lt $reminder ]]; then

          	# this one is easy, the range will be [0..3] (following our example above)
          	if [[ $current_index == 0 ]]; then
          	  left_bound=0
          	  right_bound=$elements_in_current_instance
          	  # this one will be [3..6]
          	else
          	  left_bound=$((current_index * elements_in_current_instance))
          	  right_bound=$(((current_index + 1) * elements_in_current_instance))
          	fi

          	echo "total tests : $number_of_tests, jobs: $number_of_jobs, current index : $current_index. will run tests in range : [$left_bound..$right_bound]"

          else

            # reminder can be zero here (in case of a perfect distribution): in such a case, this is just "current_index * per_instance".
          	# if reminder is not zero, we have two regions here, logically. the one of the left is "reminder * elements_in_current_instance",
          	# basically [0..3] and [3..6]
          	# and the region on the right [6..8].
          	left_bound=$((reminder * elements_in_current_instance + ((current_index - reminder) * per_instance)))
          	right_bound=$((left_bound + per_instance))

          	echo "total tests : $number_of_tests, jobs: $number_of_jobs, current index : $current_index. will run tests in range : [$left_bound..$right_bound]"
          fi

          diff=$((right_bound - left_bound))
          sliced_array=("${SORTED_TEST_CLASSNAMES[@]:$left_bound:$diff}")
          TEST_ARG=$(echo ${sliced_array[@]} | sed 's/ /,/g')

          echo "will run tests : ${TEST_ARG[@]}"

}

main