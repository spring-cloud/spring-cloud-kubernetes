#!/bin/bash

PLAIN_TEST_CLASSNAMES=($(find . -name '*.java' \
      				     | grep 'src/test/java' \
          				 | grep -v 'Fabric8IstioIT' \
          				 | xargs grep -l '@Test' \
                         | xargs grep -L 'abstract class' \
          				 | sed 's/.*src.test.java.//g' \
          				 | sed 's@/@.@g' \
          				 | sed 's/.\{5\}$//' \
          				 | tr '\n' ' '))

main() {

	ABSTRACT_TEST_CLASSNAMES_COMMAND="find . -name '*.java' \
                         | grep  'src/test/java' \
                         | grep -v 'Fabric8IstioIT' \
                         | xargs grep -l '@Test' \
                         | xargs grep -l 'abstract class' \
                         | sed 's/.*\///g' \
                         | sed 's/.java//g'"

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
	PLAIN_TEST_CLASSNAMES=$(sort <<< "${PLAIN_TEST_CLASSNAMES[*]}")
	unset IFS
	echo ${PLAIN_TEST_CLASSNAMES[@]}
}

main