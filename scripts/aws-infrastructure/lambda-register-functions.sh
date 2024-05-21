#!/bin/bash

source config.sh

echo "Creating lambda role"

aws iam create-role \
	--role-name $LAMBDA_ROLE_NAME \
	--assume-role-policy-document '{"Version": "2012-10-17","Statement": [{ "Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}, "Action": "sts:AssumeRole"}]}'

echo "Role created! Waiting for 5 seconds..."
sleep 5

echo "Attaching role policies: AWSLambdaBasicExecutionRole, AmazonDynamoDBFullAccess"
aws iam attach-role-policy \
	--role-name $LAMBDA_ROLE_NAME \
	--policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole \
	--policy-arn arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess

echo "Policies attached! Waiting for 5 seconds..."
sleep 5

echo "Creating lambda functions (REMINDER: press Q after each creation)"

echo "Creating lambda function for foxes rabbits"
aws lambda create-function \
	--function-name $LAMBDA_FUNCTION_NAME_FOXES_RABBITS \
	--zip-file fileb://../../instrumented-lambda-jars/instrumented-foxes-rabbits.jar \
  --handler pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler \
	--runtime java11 \
	--timeout 100 \
	--memory-size 512 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/$LAMBDA_ROLE_NAME

echo "Creating lambda function for insect war"
aws lambda create-function \
	--function-name $LAMBDA_FUNCTION_NAME_INSECT_WAR \
	--zip-file fileb://../../instrumented-lambda-jars/instrumented-insect-war.jar \
  --handler pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler \
	--runtime java11 \
	--timeout 100 \
  --memory-size 512 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/$LAMBDA_ROLE_NAME

echo "Creating lambda function for compress image"
aws lambda create-function \
	--function-name $LAMBDA_FUNCTION_NAME_COMPRESS_IMAGE \
	--zip-file fileb://../../compression/target/compression-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --handler pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl \
	--runtime java11 \
	--timeout 100 \
  --memory-size 512 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/$LAMBDA_ROLE_NAME

echo "Done!"
