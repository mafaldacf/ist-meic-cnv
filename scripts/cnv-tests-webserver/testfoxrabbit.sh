#!/bin/bash

# Syntax:  ./testfoxrabbit.sh <ip> <port> <generations> <world> <scenario>
# Example: ./testfoxrabbit.sh 18.189.182.115 8000 100 4 2
HOST=$1
PORT=$2
GENERATIONS=$3
WORLD=$4
SCENARIO=$5

function test_batch_requests {
	REQUESTS=89
	CONNECTIONS=5
	ab -s 120 -n $REQUESTS -c $CONNECTIONS $HOST:$PORT/simulate\?generations=$GENERATIONS\&world=$WORLD\&scenario=$SCENARIO
}

function test_single_requests {
	curl $HOST:$PORT/simulate\?generations=$GENERATIONS\&world=$WORLD\&scenario=$SCENARIO
}

#test_single_requests
test_batch_requests