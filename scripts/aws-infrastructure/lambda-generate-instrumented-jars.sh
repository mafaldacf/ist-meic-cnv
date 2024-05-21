#!/bin/bash

source config.sh

echo "[IMPORTANT NOTE] Before proceeding, make sure you have an output directory in the project folder with the necessary bytecode!"
echo "This is crucial in order to generate jars for foxes rabbits and insect war with instrumented bytecode."

sleep 5

cd ../../ && mvn clean compile install

generate_jar_instrumented_bytecode() {
  app_name=$1
  module_name=$2
  echo "Generating jar files with instrumented bytecode for ${app_name}"

  # clean environment
  cd ${app_name} && rm -rf bytecode
  # retrieve original bytecode
  unzip target/${module_name}-1.0.0-SNAPSHOT-jar-with-dependencies.jar -d bytecode
  # insert app instrumented bytecode available in the output directory
  cp -r ../output/pt/ulisboa/tecnico/cnv/${module_name} bytecode/pt/ulisboa/tecnico/cnv/
  # generate jar with instrumented bytecode
  cd bytecode && jar cvf ../../instrumented-lambda-jars/instrumented-${app_name}.jar * && cd .. && rm -rf bytecode

  cd ..
}

generate_jar_instrumented_bytecode foxes-rabbits foxrabbit
generate_jar_instrumented_bytecode insect-war insectwar

echo ""
echo "Done! Jars with instrumented bytecode are now available in 'instrumented-lambda-jars' folder."
