#!/bin/bash


source ./access.sh

# Account 1: Camunda, Kafka, Telemetry, AssetLink, Prosumer, UtilityOperator, GridBalancing, RDS
cd Quarkus-Terraform/telemetry
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/assetlink
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/prosumer
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/utilityoperator
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/gridbalancing
terraform destroy -auto-approve
cd ../..

cd RDS-Terraform
terraform destroy -auto-approve
cd ..

cd Camunda-Terraform
terraform destroy -auto-approve
cd ..

cd Kafka
terraform destroy -auto-approve
cd ..

# Switch to Account 2 — FlexibilityForecasting, FlexibilityEmission, EnergyAnalytics, Kong, Konga, Ollama
use_account2

cd Quarkus-Terraform/flexibilityforecasting
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/flexibilityemission
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/energyanalytics
terraform destroy -auto-approve
cd ../..

cd KongTerraform
terraform destroy -auto-approve
cd ..

cd KongaTerraform
terraform destroy -auto-approve
cd ..

cd Ollama-Terraform
terraform destroy -auto-approve
cd ..
