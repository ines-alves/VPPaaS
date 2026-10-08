#!/bin/bash
# check-kafka.sh
# Usage: ./check-kafka.sh <broker-public-ip-or-dns>

BROKER_HOST="${1}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KEY_FILE="$SCRIPT_DIR/Kafka/key.pem"
KAFKA_DIR="/kafka_2.13-4.1.1"
SSH_USER="ec2-user"
SSH_OPTS="-i $KEY_FILE -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes"

if [ -z "$BROKER_HOST" ]; then
    echo "Usage: $0 <broker-public-ip-or-dns>"
    echo ""
    echo "  <broker-public-ip-or-dns>  Public IP or DNS of any Kafka broker EC2 instance."
    echo ""
    echo "Example:"
    echo "  $0 ec2-1-2-3-4.compute-1.amazonaws.com"
    exit 1
fi

if [ ! -f "$KEY_FILE" ]; then
    echo "ERROR: SSH key not found at $KEY_FILE"
    exit 1
fi

chmod 400 "$KEY_FILE" 2>/dev/null

ssh_run() {
    ssh -n $SSH_OPTS "$SSH_USER@$BROKER_HOST" "$@" 2>/dev/null
}

divider() { echo "──────────────────────────────────────────────────────────────────────"; }

echo ""
divider
echo "  Kafka Cluster Check"
echo "  Broker : $BROKER_HOST"
echo "  Key    : $KEY_FILE"
divider

echo ""
echo "Testing SSH connectivity..."
if ! ssh_run "echo ok" | grep -q "ok"; then
    echo "    ERROR: Cannot reach $BROKER_HOST via SSH."
    exit 1
fi
echo "    SSH connection OK."


echo ""
echo "Checking Kafka process..."
KAFKA_PROCS=$(ssh_run "ps -ef | grep 'kafka.Kafka' | grep -v grep | wc -l")
if [ "${KAFKA_PROCS:-0}" -gt 0 ]; then
    echo "    Kafka is running (${KAFKA_PROCS} process(es) found)."
else
    echo "    No Kafka process detected."
fi

echo ""
echo "Listing topics..."
RAW_TOPICS=$(ssh_run "cd $KAFKA_DIR && bin/kafka-topics.sh --bootstrap-server localhost:9092 --list")

if [ -z "$RAW_TOPICS" ]; then
    echo "    No topics found."
    echo ""
    exit 0
fi

# Filter out internal topics
TOPICS=$(echo "$RAW_TOPICS" | grep -v "^__")
INTERNAL=$(echo "$RAW_TOPICS" | grep "^__")

TOPIC_COUNT=$(echo "$TOPICS" | grep -c .)
INTERNAL_COUNT=$(echo "$INTERNAL" | grep -c .)

echo "    Found $TOPIC_COUNT application topic(s)"
echo ""
echo "    Topics:"
echo "$TOPICS" | while IFS= read -r t; do
    echo "      - $t"
done

echo ""
echo "Message count per topic..."
echo ""
printf "    %-42s  %10s  %10s\n" "TOPIC" "PARTITIONS" "MESSAGES"
echo "    ────────────────────────────────────────────────────────────────"

HAS_MESSAGES=0
EMPTY_TOPICS=()

while IFS= read -r topic; do
    [ -z "$topic" ] && continue

    # kafka-get-offsets.sh outputs  topic:partition:offset  per line
    OFFSETS=$(ssh_run "cd $KAFKA_DIR && bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic '$topic' --time latest")

    PART_COUNT=$(echo "$OFFSETS" | grep -c .)
    TOTAL=$(echo "$OFFSETS" | awk -F: '{s += $NF} END {print s+0}')

    if [ "${TOTAL:-0}" -gt 0 ]; then
        HAS_MESSAGES=1
    else
        EMPTY_TOPICS+=("$topic")
    fi

    printf "    %-42s  %10s  %10s%s\n" "$topic" "$PART_COUNT" "$TOTAL" "$INDICATOR"
done <<< "$TOPICS"


echo ""
echo "Topic details (partition / replication / leader info)..."
echo ""
ssh_run "cd $KAFKA_DIR && bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe" | grep -v "^__" | sed 's/^/    /'


echo ""
echo "Looking at all messages per topic that has data..."
echo ""

if [ "$HAS_MESSAGES" -eq 0 ]; then
    echo "    No topics with messages found."
else
    while IFS= read -r topic; do
        [ -z "$topic" ] && continue
        TOTAL=$(ssh_run "cd $KAFKA_DIR && bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic '$topic' --time latest" | awk -F: '{s += $NF} END {print s+0}')
        [ "${TOTAL:-0}" -eq 0 ] && continue

        echo "    ── $topic ──"
        ssh_run "cd $KAFKA_DIR && bin/kafka-console-consumer.sh \
            --bootstrap-server localhost:9092 \
            --topic '$topic' \
            --from-beginning \
            --timeout-ms 5000" 2>/dev/null | sed 's/^/    /'
        echo ""
    done <<< "$TOPICS"
fi

