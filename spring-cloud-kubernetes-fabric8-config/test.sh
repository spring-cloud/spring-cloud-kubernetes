#!/bin/bash


main() {

    # - find all tests
            # - exclude Fabric8IstionIT
            # - only take classes that have @Test inside them
            # - ignore the ones that have 'abstract class'. we do this because otherwise we would pass
            #   to -DtestsToRun an abstract class, and it will not run anything.
            # - drop the "begining" xxx/src/test/java
            # - replace / with .
            # - drop last ".java"
            # - replace newline with space

            PLAIN_TEST_CLASSNAMES=($(find . -name '*.java' \
                    | grep 'src/test/java' \
                    | grep -v 'Fabric8IstioIT' \
                    | xargs grep -l '@Test' \
                    | xargs grep -L 'abstract class' \
                    | sed 's/.*src.test.java.//g' \
                    | sed 's@/@.@g' \
                    | sed 's/.\{5\}$//'))

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
                    | sed 's/.\{5\}$//'"

            while read class_name; do
              replaced=$(echo ${DERIVED_FROM_ABSTRACT_CLASSES_COMMAND/replace_me/"$class_name"})
              result=($(eval $replaced))
              PLAIN_TEST_CLASSNAMES+=(${result[@]})
            done < <(eval $ABSTRACT_TEST_CLASSNAMES_COMMAND)

            IFS=$'\n'
            SORTED_TEST_CLASSNAMES=( $(sort <<< "${PLAIN_TEST_CLASSNAMES[*]}") )
            unset IFS

            # split in half, because of the issue below, and failure in the circleci with error:
            # "Error: failed to read input: bufio.Scanner: token too long"
            ########## https://github.com/CircleCI-Public/circleci-cli/pull/441
            size="${#SORTED_TEST_CLASSNAMES[@]}"
            let leftBound="$size/2"
            let rightBound="$size - $leftBound"

            left=( "${SORTED_TEST_CLASSNAMES[@]:0:$leftBound}" )
            right=( "${SORTED_TEST_CLASSNAMES[@]:$leftBound:$rightBound}" )

            echo "left is : ${left[@]}"
            echo "right is : ${right[@]}"

            # CLASSNAMES=()
            # LEFT_CLASSNAMES=$(echo ${left[@]} | tr '\n' ' ' | circleci tests split --split-by=timings)
            # RIGHT_CLASSNAMES=$(echo ${right[@]} | tr '\n' ' ' | circleci tests split --split-by=timings)

            # CLASSNAMES+=(${LEFT_CLASSNAMES[@]})
            # CLASSNAMES+=(${RIGHT_CLASSNAMES[@]})

            # echo $CLASSNAMES
            # TEST_ARG=$(echo $CLASSNAMES | sed 's/ /,/g')
            # echo $TEST_ARG
    
}

main


