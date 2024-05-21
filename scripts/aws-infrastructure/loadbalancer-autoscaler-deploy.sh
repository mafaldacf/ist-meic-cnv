#!/bin/bash

source config.sh

# Launch a vm instance.
$DIR/ec2-launch-vm.sh

# Install java.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd

# Clean generated targets by maven
cd ../.. && mvn clean compile install && cd scripts/aws-infrastructure

# Install load balancer's jar
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH -r ../../awsmanagement/target/awsmanagement-1.0.0-SNAPSHOT-jar-with-dependencies.jar ec2-user@$(cat tmp/instance.dns):

# Prepare environment with Instance ID
cmd="sudo yum install maven -y"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd

# Launch
cmd="export IID=$(cat tmp/instance.id) && java -cp awsmanagement-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.awsmanagement.Main"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd