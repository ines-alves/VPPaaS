#!/bin/bash
esc=$'\033'

# Helper: extract public DNS from current directory's terraform state
get_dns() {
  terraform state show "$1" \
    | grep public_dns \
    | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" \
    | sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"
}

cd KongTerraform
KONG_SERVER_ADDRESS="$(get_dns aws_instance.Kong)"
cd ..

cd Quarkus-Terraform/prosumer
PROSUMER_HOST="$(get_dns aws_instance.Prosumer)"
cd ../..

cd Quarkus-Terraform/assetlink
ASSETLINK_HOST="$(get_dns aws_instance.AssetLink)"
cd ../..

cd Quarkus-Terraform/utilityoperator
UTILITYOPERATOR_HOST="$(get_dns aws_instance.UtilityOperator)"
cd ../..

cd Quarkus-Terraform/telemetry
TELEMETRY_HOST="$(get_dns aws_instance.Telemetry)"
cd ../..

cd Quarkus-Terraform/flexibilityforecasting
FLEXIBILITYFORECASTING_HOST="$(get_dns aws_instance.FlexibilityForecasting)"
cd ../..

cd Quarkus-Terraform/gridbalancing
GRIDBALANCING_HOST="$(get_dns aws_instance.GridBalancing)"
cd ../..

cd Quarkus-Terraform/energyanalytics
ENERGYANALYTICS_HOST="$(get_dns aws_instance.EnergyAnalytics)"
cd ../..

cd Quarkus-Terraform/flexibilityemission
FLEXIBILITYEMISSION_HOST="$(get_dns aws_instance.FlexibilityEmission)"
cd ../..

cd Ollama-Terraform
OLLAMA_HOST="http://$(terraform state show 'aws_instance.exampleOllamaConfiguration[0]' |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):11434"
cd ..

echo "Waiting for Kong admin API to become available..."
until curl -s --max-time 5 "http://${KONG_SERVER_ADDRESS}:8001" > /dev/null 2>&1; do
  sleep 10
done
echo "Kong is ready."

echo "----Starting the provisioning of the KONG commands -----"
echo "KONG SERVER              = $KONG_SERVER_ADDRESS"
echo "PROSUMER HOST            = $PROSUMER_HOST"
echo "ASSETLINK HOST           = $ASSETLINK_HOST"
echo "UTILITYOPERATOR HOST     = $UTILITYOPERATOR_HOST"
echo "TELEMETRY HOST           = $TELEMETRY_HOST"
echo "FORECASTING HOST         = $FLEXIBILITYFORECASTING_HOST"
echo "GRIDBALANCING HOST       = $GRIDBALANCING_HOST"
echo "ENERGYANALYTICS HOST     = $ENERGYANALYTICS_HOST"
echo "FLEXIBILITYEMISSION HOST = $FLEXIBILITYEMISSION_HOST"
echo "OLLAMA HOST              = $OLLAMA_HOST"

# ================================================================
# CREATE SERVICES — one per microservice + Ollama
# ================================================================

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=prosumer-service" \
  --data "url=http://${PROSUMER_HOST}:8080"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=assetlink-service" \
  --data "url=http://${ASSETLINK_HOST}:8080"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=utilityoperator-service" \
  --data "url=http://${UTILITYOPERATOR_HOST}:8080"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=telemetry-service" \
  --data "url=http://${TELEMETRY_HOST}:8080"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=flexibilityforecasting-service" \
  --data "url=http://${FLEXIBILITYFORECASTING_HOST}:8080"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=gridbalancing-service" \
  --data "url=http://${GRIDBALANCING_HOST}:8080"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=energyanalytics-service" \
  --data "url=http://${ENERGYANALYTICS_HOST}:8080"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=flexibilityemission-service" \
  --data "url=http://${FLEXIBILITYEMISSION_HOST}:8080"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
  --data "name=ollama-service" \
  --data "url=${OLLAMA_HOST}" \
  --data "read_timeout=180000" \
  --data "write_timeout=180000" \
  --data "connect_timeout=180000"

# ================================================================
# CREATE ROUTES — static endpoints (strip_path=false, path forwarded as-is)
# ================================================================

# -- Prosumer --
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data "paths[]=/Prosumer" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data "paths[]=/Prosumer/createAsset" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data "paths[]=/Prosumer/assets" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data "paths[]=/Asset/battery" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data "paths[]=/Asset/solar" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data "paths[]=/Asset/ev" \
  --data "strip_path=false"

# -- AssetLink --
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/assetlink-service/routes" \
  --data "paths[]=/AssetLink" \
  --data "strip_path=false"

# -- UtilityOperator --
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
  --data "paths[]=/UtilityOperator" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
  --data "paths[]=/UtilityOperator/createGridCell" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
  --data "paths[]=/GridCell" \
  --data "strip_path=false"

# -- Telemetry --
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
  --data "paths[]=/Telemetry/assets" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
  --data "paths[]=/Telemetry/Consume" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
  --data "paths[]=/Telemetry/grid-zones" \
  --data "strip_path=false"

# -- FlexibilityForecasting --
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityforecasting-service/routes" \
  --data "paths[]=/FlexibilityForecasting/buildPrompt" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityforecasting-service/routes" \
  --data "paths[]=/FlexibilityForecasting/createForecast" \
  --data "strip_path=false"

# -- Ollama (strip_path=true: strips /Ollama, forwards to /api/generate baked into the service URL) --

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/ollama-service/routes" \
  --data "paths[]=/api/generate" \
  --data "methods[]=POST" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/ollama-service/routes" \
  --data "paths[]=/api/tags" \
  --data "methods[]=GET" \
  --data "strip_path=false"

# -- GridBalancing --
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
  --data "paths[]=/GridBalancing/analyze" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
  --data "paths[]=/GridBalancing/recommendation" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
  --data "paths[]=/GridBalancing/recommendation/publish" \
  --data "strip_path=false"

# -- EnergyAnalytics --
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
  --data "paths[]=/analytics/consumed" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
  --data "paths[]=/analytics/discharged" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
  --data "paths[]=/analytics/generated" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
  --data "paths[]=/analytics/soc" \
  --data "strip_path=false"

# -- FlexibilityEmission --
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityemission-service/routes" \
  --data "paths[]=/FlexibilityOffer/analyze" \
  --data "strip_path=false"

curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityemission-service/routes" \
  --data "paths[]=/FlexibilityOffer/publish" \
  --data "strip_path=false"

# /FlexibilityOffer/prosumer/{prosumerID}
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityemission-service/routes" \
  --data-urlencode "paths[]=~/FlexibilityOffer/prosumer/(?<prosumerID>\d+)" \
  --data "strip_path=false"

# ================================================================
# CREATE ROUTES — parameterised endpoints (strip_path=false)
# ================================================================

# /Prosumer/{id}
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data-urlencode "paths[]=~/Prosumer/(?<id>\d+)$" \
  --data "strip_path=false"

# /Prosumer/{id}/ev
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data-urlencode "paths[]=~/Prosumer/(?<id>\d+)/ev" \
  --data "strip_path=false"

# /Prosumer/{id}/solar
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
  --data-urlencode "paths[]=~/Prosumer/(?<id>\d+)/solar" \
  --data "strip_path=false"

# /AssetLink/{prosumerID}/{utilityOperatorID}
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/assetlink-service/routes" \
  --data-urlencode "paths[]=~/AssetLink/(?<prosumerID>\d+)/(?<utilityOperatorID>\d+)" \
  --data "strip_path=false"

# /UtilityOperator/{id}
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
  --data-urlencode "paths[]=~/UtilityOperator/(?<id>\d+)$" \
  --data "strip_path=false"

# /UtilityOperator/gridcells/{cellId}/neighbors
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
  --data-urlencode "paths[]=~/UtilityOperator/gridcells/(?<cellId>\S+)/neighbors" \
  --data "strip_path=false"

# /Telemetry/asset/{assetId}
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
  --data-urlencode "paths[]=~/Telemetry/asset/(?<assetId>\S+)" \
  --data "strip_path=false"

# /Telemetry/grid-zone/{gridZoneId}
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
  --data-urlencode "paths[]=~/Telemetry/grid-zone/(?<gridZoneId>\S+)" \
  --data "strip_path=false"

# /FlexibilityForecasting/{prosumerID}
curl -i -X POST \
  --url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityforecasting-service/routes" \
  --data-urlencode "paths[]=~/FlexibilityForecasting/(?<prosumerID>\d+)" \
  --data "strip_path=false"

# ================================================================
# UPDATE BPMN FILES
# Replace every hardcoded EC2 address with the Kong gateway address.
# ================================================================

echo "----Updating BPMN files to use Kong gateway -----"
for bpmn in BPMN/*.bpmn; do
  sed -i '' "s|http://ec2-[^/]*|http://${KONG_SERVER_ADDRESS}:8000|g" "$bpmn"
  echo "Updated: $bpmn"
done
echo "----BPMN files updated -----"
