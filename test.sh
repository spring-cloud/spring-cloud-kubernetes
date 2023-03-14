#!/bin/bash

declare -a PLAIN_TEST_CLASSNAMES

main() {
	    echo  "base_branch : $BASE_BRANCH"
        declare -a PLAIN_TEST_CLASSNAMES
        baseBranch=$BASE_BRANCH
        if [[ $baseBranch == "2.1.x" ]]; then
        
          PLAIN_TEST_CLASSNAMES=($(find . -name '*.java' \
                        | grep 'src/test/java' \
                        | grep -v 'Fabric8IstioIT' \
                        | xargs grep -l '@Test' \
                        | xargs grep -L 'abstract class' \
                        | sed 's/.*src.test.java.//g' \
                        | sed 's@/@.@g' \
                        | sed 's/.\{5\}$//' \
                        | tr '\n' ' '))
        else
          PLAIN_TEST_CLASSNAMES=($(find . -name '*.java' \
                        | grep 'src/test/java' \
                        | grep -v 'Fabric8IstioIT' \
                        | xargs grep -l '@Test' \
                        | xargs grep -L 'abstract class' \
                        | sed 's/.*src.test.java.//g' \
                        | sed 's@/@.@g' \
                        | sed 's/.\{5\}$//' \
                        | tr '\n' ' '))
        fi
}

main