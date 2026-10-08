#!/bin/bash
set -e
source ./access.sh

# # #Terraform - Quarkus Micro services changing the configuration of the DB connection, recompiling and packaging
CompileCode() {
    sed -i '' "/quarkus.datasource.reactive.url/d" application.properties
    sed -i '' "/quarkus.container-image.group/d" application.properties
    sed -i '' "/quarkus.container-image.push/d" application.properties
    echo "" >> application.properties
    echo "quarkus.container-image.group=$DockerUsername" >> application.properties
    echo "quarkus.container-image.push=true" >> application.properties
    echo "quarkus.datasource.reactive.url=mysql://$addressDB:3306/VPPaaS" >> application.properties                                        
    cd ../../..
    DockerImage="$(grep -m 1 "<artifactId>" pom.xml|sed "s/<artifactId>//g"|sed "s/<\/artifactId>//g" |sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g")"
    DockerImageVersion="$(grep -m 1 "<version>" pom.xml|sed "s/<version>//g"|sed "s/<\/version>//g" |sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g")"
    ./mvnw clean package
    cd ../..
}

DeployMicroservice() {
    local instanceName=$1
    sed -i '' "/sudo docker login/d" quarkus.sh
    sed -i '' "/sudo docker pull/d" quarkus.sh
    sed -i '' "/sudo docker run/d" quarkus.sh
    echo "sudo docker login -u \"$DockerUsername\" -p \"$DockerPassword\"" >> quarkus.sh
    echo "sudo docker pull $DockerUsername/$DockerImage:$DockerImageVersion" >> quarkus.sh
    echo "sudo docker run -d --name $DockerImage -p 8080:8080 $DockerUsername/$DockerImage:$DockerImageVersion" >> quarkus.sh
    terraform init
    terraform apply -auto-approve
    cd ../..
}

# # #Terraform - RDS
cd RDS-Terraform
terraform init && terraform apply -auto-approve
esc=$'\e'
addressDB="$(terraform state show aws_db_instance.example |grep address | sed "s/address//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
cd ..

# # #Terraform - Camunda
cd Camunda-Terraform
terraform init && terraform apply -auto-approve
cd ..

# # #Terraform - Kafka
cd Kafka
terraform init && terraform apply -auto-approve
esc=$'\e'
addresskafka="$(terraform output -json publicdnslist | tr -d '[\n\" ]' | sed 's/,/:9092,/g' | sed 's/$/:9092/' )"
cd ..


cd microservices/Telemetry/src/main/resources
sed -i '' "/kafka.bootstrap.servers/d" application.properties
echo "kafka.bootstrap.servers=$addresskafka" >> application.properties
CompileCode
cd Quarkus-Terraform/telemetry
DeployMicroservice Telemetry

cd microservices/AssetLink/src/main/resources
sed -i '' "/kafka.bootstrap.servers/d" application.properties
echo "kafka.bootstrap.servers=$addresskafka" >> application.properties
CompileCode
cd Quarkus-Terraform/assetlink
DeployMicroservice AssetLink

cd microservices/Prosumer/src/main/resources
CompileCode
cd Quarkus-Terraform/prosumer
DeployMicroservice Prosumer


cd microservices/UtilityOperator/src/main/resources
CompileCode
cd Quarkus-Terraform/utilityoperator
DeployMicroservice UtilityOperator

cd microservices/GridBalancing/src/main/resources
sed -i '' "/kafka.bootstrap.servers/d" application.properties
echo "kafka.bootstrap.servers=$addresskafka" >> application.properties
CompileCode
cd Quarkus-Terraform/gridbalancing
DeployMicroservice GridBalancing

# Switch to Account 2 — FlexibilityForecasting, FlexibilityEmission, EnergyAnalytics, Kong, Konga, Ollama
use_account2

cd microservices/FlexibilityForecasting/src/main/resources
CompileCode
cd Quarkus-Terraform/flexibilityforecasting
DeployMicroservice FlexibilityForecasting

cd microservices/FlexibilityEmission/src/main/resources
sed -i '' "/kafka.bootstrap.servers/d" application.properties
echo "kafka.bootstrap.servers=$addresskafka" >> application.properties
CompileCode
cd Quarkus-Terraform/flexibilityemission
DeployMicroservice FlexibilityEmission

cd microservices/EnergyAnalytics/src/main/resources
sed -i '' "/kafka.bootstrap.servers/d" application.properties
echo "kafka.bootstrap.servers=$addresskafka" >> application.properties
CompileCode
cd Quarkus-Terraform/energyanalytics
DeployMicroservice EnergyAnalytics

# # #Terraform - Ollama
cd Ollama-Terraform
terraform init && terraform apply -auto-approve
cd ..

# #Terraform 1 - Kong
cd KongTerraform
terraform init && terraform apply -auto-approve
cd ..

# #Terraform 2 - Konga
cd KongaTerraform
terraform init && terraform apply -auto-approve
cd ..



# Showing all the PUBLIC_DNSs
cd Camunda-Terraform
echo "CAMUNDA IS AVAILABLE HERE:"
addressCamunda="$(terraform state show aws_instance.Camunda |grep public_dns| sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressCamunda":8080/operate"
echo "http://"$addressCamunda":8080/swagger-ui/index.html?urls.primaryName=Orchestration+Cluster+API"
echo
cd ..

cd Kafka
echo "KAFKA IS AVAILABLE HERE:"
echo ""$addresskafka""
echo
cd ..

cd Quarkus-Terraform/telemetry
echo "MICROSERVICE telemetry IS AVAILABLE HERE:"
addressMS="$(terraform state show aws_instance.Telemetry |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressMS":8080/q/swagger-ui/"
echo
cd ../..

cd Quarkus-Terraform/prosumer
echo "MICROSERVICE prosumer IS AVAILABLE HERE:"
addressMS="$(terraform state show aws_instance.Prosumer |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressMS":8080/q/swagger-ui/"
echo
cd ../..

cd Quarkus-Terraform/utilityoperator
echo "MICROSERVICE utilityoperator IS AVAILABLE HERE:"
addressMS="$(terraform state show aws_instance.UtilityOperator |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressMS":8080/q/swagger-ui/"
echo
cd ../..

cd Quarkus-Terraform/assetlink
echo "MICROSERVICE assetlink IS AVAILABLE HERE:"
addressMS="$(terraform state show aws_instance.AssetLink |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressMS":8080/q/swagger-ui/"
echo
cd ../..

cd Quarkus-Terraform/gridbalancing
echo "MICROSERVICE gridbalancing IS AVAILABLE HERE:"
addressMS="$(terraform state show aws_instance.GridBalancing |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressMS":8080/q/swagger-ui/"
echo
cd ../..

cd Quarkus-Terraform/energyanalytics
echo "MICROSERVICE energyanalytics IS AVAILABLE HERE:"
addressMS="$(terraform state show aws_instance.EnergyAnalytics |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressMS":8080/q/swagger-ui/"
echo
cd ../..

cd Quarkus-Terraform/flexibilityemission
echo "MICROSERVICE flexibilityemission IS AVAILABLE HERE:"
addressMS="$(terraform state show aws_instance.FlexibilityEmission |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressMS":8080/q/swagger-ui/"
echo
cd ../..

cd Quarkus-Terraform/flexibilityforecasting
echo "MICROSERVICE flexibilityforecasting IS AVAILABLE HERE:"
addressMS="$(terraform state show aws_instance.FlexibilityForecasting |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressMS":8080/q/swagger-ui/"
echo
cd ../..

cd RDS-Terraform
echo "RDS IS AVAILABLE HERE:"
terraform state show aws_db_instance.example |grep address
terraform state show aws_db_instance.example |grep port
echo
cd ..

echo "KONG IS AVAILABLE HERE:" 
cd KongTerraform
addressKong="$(terraform state show aws_instance.Kong |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressKong":8000/"
echo
cd ..

echo "KONGA IS AVAILABLE HERE:"
cd KongaTerraform
addressKonga="$(terraform state show aws_instance.Konga |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressKonga":1337/"
echo
cd ..

echo "OLLAMA IS AVAILABLE HERE:"
cd Ollama-Terraform
addressOllama="$(terraform state show 'aws_instance.exampleOllamaConfiguration[0]' |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g" )"
echo "http://"$addressOllama":11434/"
echo
cd ..

# Kong configuration
bash ./kongCommands.sh

echo "Deploying all the Camunda forms..."
for entry in ./BPMN/forms/*.form
do
  entry=${entry:2}
  echo "$entry" 
  curl -L -X POST "http://$addressCamunda:8080/v2/deployments" \
  -H "Content-Type: multipart/form-data" \
  -H "Accept: application/json" \
  -F "resources=@$entry"
done

echo "Deploying all the Camunda business processes..."
for entry in ./BPMN/*.bpmn
do
  entry=${entry:2}
  echo "$entry" 
  curl -L -X POST "http://$addressCamunda:8080/v2/deployments" \
  -H "Content-Type: multipart/form-data" \
  -H "Accept: application/json" \
  -F "resources=@$entry"
done
