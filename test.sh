#!/bin/bash


main() {

    array=(1 2 3 4 5)
    size="${#array[@]}"
let leftBound="$size/2"
let rightBound="$size - $leftBound"

left=( "${array[@]:0:$leftBound}" )
right=( "${array[@]:$leftBound:$rightBound}" )
    
    echo "left: ${left[@]}" | tr '\n' ' '
    echo "right: ${right[@]}" | tr '\n' ' '
    
}

main


