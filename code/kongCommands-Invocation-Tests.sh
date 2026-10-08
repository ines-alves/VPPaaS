#!/bin/bash
esc=$'\033'

get_dns() {
  terraform state show "$1" \
    | grep public_dns \
    | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" \
    | sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"
}

cd KongTerraform
KONG_SERVER_ADDRESS="$(get_dns aws_instance.Kong)"
cd ..

echo "KONG_SERVER_ADDRESS = $KONG_SERVER_ADDRESS"

BASE="http://${KONG_SERVER_ADDRESS}:8000"

# ================================================================
# PROSUMER
# ================================================================

# GET /Prosumer — list all prosumers
curl -i -X GET \
  --url "${BASE}/Prosumer"

# POST /Prosumer — create a prosumer
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/Prosumer" \
  --data '{"name": "TestProsumer", "FiscalNumber": 123456789, "location": "Lisbon"}'

# GET /Prosumer/{id} — get prosumer by id
curl -i -X GET \
  --url "${BASE}/Prosumer/1"

# POST /Prosumer/createAsset — create asset linked to a prosumer
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/Prosumer/createAsset" \
  --data '{"prosumerId": 1, "assetType": "BATTERY"}'

# GET /Prosumer/{id}/assets — list assets for a prosumer
curl -i -X GET \
  --url "${BASE}/Prosumer/1/assets"

# GET /Prosumer/{id}/{assetType} — list EV charger assets for a prosumer
curl -i -X GET \
  --url "${BASE}/Prosumer/1/EV_CHARGER"

# GET /Prosumer/{id}/solar — list solar inverter assets for a prosumer
curl -i -X GET \
  --url "${BASE}/Prosumer/1/solar"

# GET /Asset/battery — list all battery assets
curl -i -X GET \
  --url "${BASE}/Asset/battery"

# GET /Asset/solar — list all solar inverter assets
curl -i -X GET \
  --url "${BASE}/Asset/solar"

# GET /Asset/ev — list all EV charger assets
curl -i -X GET \
  --url "${BASE}/Asset/ev"

# ================================================================
# ASSETLINK
# ================================================================

# GET /AssetLink — list all asset links
curl -i -X GET \
  --url "${BASE}/AssetLink"

# POST /AssetLink — create an asset link between a prosumer and a utility operator
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/AssetLink" \
  --data '{"prosumerId": 2, "utilityOperatorId": 3}'

# GET /AssetLink/{prosumerID}/{utilityOperatorID} — get link by prosumer and operator ids
curl -i -X GET \
  --url "${BASE}/AssetLink/1/1"

# ================================================================
# UTILITY OPERATOR
# ================================================================

# GET /UtilityOperator — list all utility operators
curl -i -X GET \
  --url "${BASE}/UtilityOperator"

# POST /UtilityOperator — create a utility operator
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/UtilityOperator" \
  --data '{"name": "TestOperator", "FiscalNumber": 987654321, "location": "Porto"}'

# GET /UtilityOperator/{id} — get utility operator by id
curl -i -X GET \
  --url "${BASE}/UtilityOperator/1"

# POST /UtilityOperator/createGridCell — create a grid cell linked to a utility operator
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/UtilityOperator/createGridCell" \
  --data '{"id": "LISBON-RES-99", "utilityOperatorId": 1, "address": "Av. da Liberdade", "postalCode": "1250-096", "maxCapacityKw": 100.0}'

# GET /GridCell — list all grid cells
curl -i -X GET \
  --url "${BASE}/GridCell"

# GET /UtilityOperator/gridcells/{cellId}/neighbors — get neighboring grid cells
curl -i -X GET \
  --url "${BASE}/UtilityOperator/gridcells/LISBON-RES-01/neighbors"

# ================================================================
# TELEMETRY
# ================================================================

# POST /Telemetry/Consume — start a dynamic Kafka consumer for a topic
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/Telemetry/Consume" \
  --data '{"TopicName": "0-ArcoCegoLisbon"}'

# POST /Telemetry/assets — get latest telemetry records for a list of asset ids
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/Telemetry/assets" \
  --data '["BATTERY-1001", "SOLAR-1002"]'

# POST /Telemetry/grid-zones — get latest telemetry records for a list of grid zone ids
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/Telemetry/grid-zones" \
  --data '["LISBON-RES-01", "LISBON-RES-02"]'

# GET /Telemetry/asset/{assetId} — get latest telemetry for a specific asset
curl -i -X GET \
  --url "${BASE}/Telemetry/asset/BATTERY-1001"

# GET /Telemetry/grid-zone/{gridZoneId} — get latest telemetry for a specific grid zone
curl -i -X GET \
  --url "${BASE}/Telemetry/grid-zone/LISBON-RES-01"

# ================================================================
# FLEXIBILITY FORECASTING
# ================================================================

# POST /FlexibilityForecasting/buildPrompt — build Ollama prompt from a forecast request
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/FlexibilityForecasting/buildPrompt" \
  --data '{"prosumerId": 1, "telemetry": {"asset_id": "BATTERY-1001", "asset_type": "BATTERY", "payload": {"soc_percent": 80.0, "energy_available_kwh": 10.0, "active_power_kw": -2.0, "connection_status": "CONNECTED", "soh_percent": 90.0, "max_discharge_power_kw": 3.0}}, "flexibilityEvent": {"assetId": "BATTERY-1001", "socPercent": 80.0, "offerType": "UP"}}'

# POST /FlexibilityForecasting/createForecast — persist forecast from Ollama response
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/FlexibilityForecasting/createForecast" \
  --data '{"forecast": {"prosumer_id": 1, "asset_id": "BATTERY-1001", "forecast_type": "HIGH_FLEXIBILITY", "predicted_soc_percent": 80.0, "recommendation": "High SoC suggests strong upward flexibility.", "confidence_score": 0.9}}'

# GET /FlexibilityForecasting/{prosumerID} — list forecasts for a prosumer
curl -i -X GET \
  --url "${BASE}/FlexibilityForecasting/1"

# ================================================================
# OLLAMA
# ================================================================

# POST /api/generate — generate LLM response via Ollama
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/api/generate" \
  --data '{"model": "llama3.2:latest", "prompt": "Generate a flexibility forecast for a battery asset with 80% SoC.", "stream": false}'

# ================================================================
# GRID BALANCING
# ================================================================

# POST /GridBalancing/analyze — run balancing rules against telemetry and a grid cell
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/GridBalancing/analyze" \
  --data '{"telemetry": [{"assetId": "asset-001", "gridZoneId": "LISBON-RES-01", "measuredKw": 50.0}], "gridCell": {"id": "LISBON-RES-01", "maxCapacityKw": 100.0}}'

# POST /GridBalancing/recommendation — evaluate neighboring zones and produce a recommendation
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/GridBalancing/recommendation" \
  --data '{"selectedGridCell": {"id": "LISBON-RES-01", "maxCapacityKw": 100.0}, "gridCells": []}'

# POST /GridBalancing/recommendation/publish — persist a grid balancing recommendation
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/GridBalancing/recommendation/publish" \
  --data '{"id": "rec-001", "gridCellId": "LISBON-RES-01", "measuredKw": 50.0, "maxCapacityKw": 100.0, "recommendation": "INCREASE_LOAD", "timestamp": 1700000000000}'

# ================================================================
# ENERGY ANALYTICS
# ================================================================

# POST /analytics/soc — calculate state-of-charge analytics from telemetry
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/analytics/soc" \
  --data '{"telemetry": [{"assetId": "asset-001", "socPercent": 80.0, "energyAvailableKwh": 10.0}]}'

# POST /analytics/discharged — calculate discharged energy per zone from telemetry
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/analytics/discharged" \
  --data '{"telemetry": [{"assetId": "asset-001", "gridZoneId": "LISBON-RES-01", "energyAvailableKwh": 5.0}]}'

# POST /analytics/consumed — calculate consumed energy per prosumer from telemetry
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/analytics/consumed" \
  --data '{"prosumerId": "1", "telemetry": [{"assetId": "asset-001", "sessionEnergyKwh": 3.0}]}'

# POST /analytics/generated — calculate generated energy per prosumer from telemetry
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/analytics/generated" \
  --data '{"prosumerId": "1", "telemetry": [{"assetId": "asset-001", "dailyYieldKwh": 8.0}]}'

# ================================================================
# FLEXIBILITY EMISSION
# ================================================================

# POST /FlexibilityOffer/analyze — analyze a flexibility request and produce offers
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/FlexibilityOffer/analyze" \
  --data '{"prosumerId": 1, "telemetry": {"asset_id": "BATTERY-1001", "asset_type": "BATTERY", "payload": {"soc_percent": 60.0, "energy_available_kwh": 18.0, "connection_status": "ONLINE", "soh_percent": 65.0}}}'

# POST /FlexibilityOffer/publish — emit and persist a list of flexibility offers
curl -i -X POST \
  -H "Content-Type: application/json" \
  --url "${BASE}/FlexibilityOffer/publish" \
  --data '[{"prosumerId": 1, "assetId": "asset-001", "offerType": "UP", "quantityKw": 5.0}]'

# GET /FlexibilityOffer/prosumer/{prosumerID} — list flexibility offers for a prosumer
curl -i -X GET \
  --url "${BASE}/FlexibilityOffer/prosumer/1"
