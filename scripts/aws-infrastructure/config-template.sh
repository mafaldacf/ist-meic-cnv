#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

export PATH=/usr/local/bin:$PATH
export AWS_DEFAULT_REGION="us-east-2"
export AWS_ACCOUNT_ID="N/A"
export AWS_ACCESS_KEY_ID="N/A"
export AWS_SECRET_ACCESS_KEY="N/A"
export AWS_EC2_SSH_KEYPAIR_PATH="N/A e.g. /home/<user>/.ssh/key.pem"
export AWS_SECURITY_GROUP="N/A (security group id!!)"
export AWS_KEYPAIR_NAME="N/A"

# User preference
export EC2_AMI_NAME="cnv-webserver-image"
export LAMBDA_ROLE_NAME="cnv-lambda-role"
export LAMBDA_FUNCTION_NAME_FOXES_RABBITS="foxes-rabbits"
export LAMBDA_FUNCTION_NAME_INSECT_WAR="insect-war"
export LAMBDA_FUNCTION_NAME_COMPRESS_IMAGE="compress-image"