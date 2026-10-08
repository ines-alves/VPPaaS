#!/bin/bash
# create-kafka-topics.sh
#
# Usage: ./create-kafka-topics.sh
#
# Prerequisites:
#   - access.sh populated with valid AWS credentials (sourced automatically)
#   - Kafka/key.pem present and readable
#   - Kafka and Telemetry Terraform state available (terraform must be on PATH)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KEY_FILE="$SCRIPT_DIR/Kafka/key.pem"
KAFKA_DIR="/kafka_2.13-4.1.1"
SSH_USER="ec2-user"
SSH_OPTS="-i $KEY_FILE"

TOPICS=(
    "1-ArcoCegoLisbon"
    "2-PracadeBocage"
    "3-PracadaBoavista"
    "4-PracaDomFranciscoGomes"
)

divider() { echo "──────────────────────────────────────────────────────────────────────"; }


if [ ! -f "$SCRIPT_DIR/access.sh" ]; then
    echo "ERROR: access.sh not found at $SCRIPT_DIR/access.sh"
    exit 1
fi
source "$SCRIPT_DIR/access.sh"
use_account1

if [ ! -f "$KEY_FILE" ]; then
    echo "ERROR: SSH key not found at $KEY_FILE"
    exit 1
fi
chmod 400 "$KEY_FILE" 2>/dev/null


get_dns() {
    terraform state show "$1" \
        | grep 'public_dns' \
        | sed 's/.*= "\(.*\)"/\1/' \
        | tr -d ' '
}

echo ""
divider
echo "  Resolving addresses from Terraform state..."
divider

cd "$SCRIPT_DIR/Kafka"
BROKER_HOST=$(terraform output -json publicdnslist | tr -d '[]" ' | cut -d',' -f1)
cd "$SCRIPT_DIR"

if [ -z "$BROKER_HOST" ]; then
    echo "ERROR: Could not resolve Kafka broker address from Terraform state."
    exit 1
fi

cd "$SCRIPT_DIR/KongTerraform"
KONG_HOST=$(get_dns aws_instance.Kong)
cd "$SCRIPT_DIR"

if [ -z "$KONG_HOST" ]; then
    echo "ERROR: Could not resolve Kong address from Terraform state."
    exit 1
fi

KONG_URL="http://${KONG_HOST}:8000"

echo "  Kafka broker : $BROKER_HOST"
echo "  Kong proxy   : $KONG_URL"
echo ""


echo "Testing SSH connectivity to Kafka broker..."
if ! ssh -n $SSH_OPTS "$SSH_USER@$BROKER_HOST" "echo ok" 2>/dev/null | grep -q "ok"; then
    echo "ERROR: Cannot reach $BROKER_HOST via SSH."
    exit 1
fi
echo "  SSH connection OK."
echo ""


divider
echo "  Creating Kafka topics..."
divider
echo ""

for TOPIC in "${TOPICS[@]}"; do
    echo "  Creating topic: $TOPIC"
    RESULT=$(ssh -n $SSH_OPTS "$SSH_USER@$BROKER_HOST" \
        "cd $KAFKA_DIR && bin/kafka-topics.sh \
         --bootstrap-server localhost:9092 \
         --create --if-not-exists \
         --topic '$TOPIC' \
         --partitions 1 \
         --replication-factor 1" 2>/dev/null)
    echo "    $RESULT"
done

echo ""


echo "  Verifying topics..."
EXISTING=$(ssh -n $SSH_OPTS "$SSH_USER@$BROKER_HOST" \
    "cd $KAFKA_DIR && bin/kafka-topics.sh --bootstrap-server localhost:9092 --list" 2>/dev/null)

ALL_OK=true
for TOPIC in "${TOPICS[@]}"; do
    if echo "$EXISTING" | grep -q "^${TOPIC}$"; then
        echo "    [OK] $TOPIC"
    else
        echo "    [MISSING] $TOPIC"
        ALL_OK=false
    fi
done

echo ""
if [ "$ALL_OK" = false ]; then
    echo "ERROR: One or more topics were not created. Aborting Telemetry registration."
    exit 1
fi


divider
echo "  Registering Telemetry consumers via Kong ($KONG_URL)..."
divider
echo ""

for TOPIC in "${TOPICS[@]}"; do
    echo "  Registering consumer for: $TOPIC"
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "${KONG_URL}/Telemetry/Consume" \
        -H 'Content-Type: application/json' \
        -d "{\"TopicName\":\"${TOPIC}\"}")
    if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "204" ]; then
        echo "    [OK] HTTP $HTTP_STATUS"
    else
        echo "    [WARN] HTTP $HTTP_STATUS — check Telemetry service logs"
    fi
done

echo ""
divider
echo "  Done. The VPPaaS Event Producer can now be started."
divider
echo ""
