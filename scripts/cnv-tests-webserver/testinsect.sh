#!/bin/bash

# Syntax:  ./testinsect.sh <ip> <port> <max> <army1> <army2>
# Example: ./testinsect.sh 18.118.24.203 8000 10 10 10
HOST=$1
PORT=$2
max=$3
army1=$4
army2=$5

function test_batch_requests {
	REQUESTS=25
	CONNECTIONS=1
	ab -s 120 -n $REQUESTS -c $CONNECTIONS $HOST:$PORT/insectwar\?max=$max\&army1=$army1\&army2=$army2
}

function test_single_requests {

	curl $HOST:$PORT/insectwar\?max=$max\&army1=$army1\&army2=$army2
}

test_single_requests
test_batch_requests