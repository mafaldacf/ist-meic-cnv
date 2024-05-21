#!/bin/bash

source config.sh

# ----------------------------
# Step 1: Launch a VM instance
# ----------------------------
$DIR/ec2-launch-vm.sh

# -------------------------------------------
# Step 2: Install software in the VM instance
# -------------------------------------------

# Install java.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd

# Compile web server.
cd ../.. && mvn compile install && cd scripts/aws-infrastructure
ec2_dir="/home/ec2-user"

# Install web server with javassist
ec2_dir="/home/ec2-user"
webserver_jar="webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ../../webserver/target/${webserver_jar} ec2-user@$(cat tmp/instance.dns):
javassist_jar="JavassistWrapper-1.0-jar-with-dependencies.jar"
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ../../javassist/target/${javassist_jar} ec2-user@$(cat tmp/instance.dns):

# AWS CREDENTIALS
cmd="echo \"export AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID}\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd
cmd="echo \"export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd
cmd="echo \"export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd

# Setup web server to start on instance launch.
run_webserver="java -cp ${ec2_dir}/${webserver_jar} -Xbootclasspath/a:${ec2_dir}/${javassist_jar} -javaagent:${ec2_dir}/${webserver_jar}=ThreadICount:pt.ulisboa.tecnico.cnv.compression,pt.ulisboa.tecnico.cnv.insectwar,pt.ulisboa.tecnico.cnv.foxrabbit,javax.imageio:output pt.ulisboa.tecnico.cnv.webserver.WebServer"
cmd="echo \"${run_webserver}\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat tmp/instance.dns) $cmd

# -------------------------
# Step 3: test VM instance
# -------------------------

# Requesting an instance reboot.
aws ec2 reboot-instances --instance-ids $(cat tmp/instance.id)
echo "Rebooting instance to test web server auto-start."

# Letting the instance shutdown.
sleep 1

# Wait for port 8000 to become available.
while ! nc -z $(cat tmp/instance.dns) 8000; do
	echo "Waiting for $(cat tmp/instance.dns):8000..."
	sleep 0.5
done

# Sending a query!
echo "Sending a query!"
curl $(cat tmp/instance.dns):8000/simulate/?generations=1\&world=1\&scenario=1

# ------------------------------------------
# Step 4: create and wait for VM image (AMI)
# ------------------------------------------

aws ec2 create-image --instance-id $(cat tmp/instance.id) --name $EC2_AMI_NAME | jq -r .ImageId > tmp/image.id
echo "New VM image with id $(cat tmp/image.id)."
echo "Waiting for image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --filters Name=name,Values=EC2_AMI_NAME
echo "Waiting for image to be ready... done! \o/"

# ---------------------------------
# Step 5: Terminate the vm instance
# ---------------------------------
aws ec2 terminate-instances --instance-ids $(cat tmp/instance.id)
