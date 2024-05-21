#!/bin/bash

source config.sh

# Run new instance.
aws ec2 run-instances \
	--image-id resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2 \
	--instance-type t2.micro \
	--key-name $AWS_KEYPAIR_NAME \
	--security-group-ids $AWS_SECURITY_GROUP \
	--monitoring Enabled=true | jq -r ".Instances[0].InstanceId" > tmp/instance.id
echo "New instance with id $(cat tmp/instance.id)."

# Wait for instance to be running.
aws ec2 wait instance-running --instance-ids $(cat tmp/instance.id)
echo "New instance with id $(cat tmp/instance.id) is now running."

# Extract DNS nane.
aws ec2 describe-instances \
	--instance-ids $(cat tmp/instance.id) | jq -r ".Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" > tmp/instance.dns
echo "New instance with id $(cat tmp/instance.id) has address $(cat tmp/instance.dns)."

# Wait for instance to have SSH ready.
while ! nc -z $(cat tmp/instance.dns) 22; do
	echo "Waiting for $(cat tmp/instance.dns):22 (SSH)..."
	sleep 0.5
done
echo "New instance with id $(cat tmp/instance.id) is ready for SSH access."

# Copy aws credentials
cmd="mkdir -p /home/ec2-user/.aws/"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH -r ~/.aws/credentials ec2-user@$(cat tmp/instance.dns):/home/ec2-user/.aws/credentials
cmd="sudo chmod 777 /home/ec2-user/.aws/*"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd
