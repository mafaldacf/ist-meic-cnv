#!/bin/bash

source config.sh

aws lambda delete-function --function-name $LAMBDA_FUNCTION_NAME_FOXES_RABBITS
aws lambda delete-function --function-name $LAMBDA_FUNCTION_NAME_INSECT_WAR
aws lambda delete-function --function-name $LAMBDA_FUNCTION_NAME_COMPRESS_IMAGE

aws iam detach-role-policy \
	--role-name $LAMBDA_ROLE_NAME \
	--policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam delete-role --role-name $LAMBDA_ROLE_NAME
