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

              if [[ ${#result[@]} -ne 2 ]]; then
                    echo $class_name
              fi      
              PLAIN_TEST_CLASSNAMES+=(${result[@]})
            done < <(eval $ABSTRACT_TEST_CLASSNAMES_COMMAND)

            IFS=$'\n'
            SORTED_TEST_CLASSNAMES=( $(sort <<< "${PLAIN_TEST_CLASSNAMES[*]}" | uniq -u) )
            unset IFS

            printf "%s\n" "${SORTED_TEST_CLASSNAMES[@]}" > left.txt
    
}

main


