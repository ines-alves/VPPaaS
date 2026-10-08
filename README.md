# VPPaaS — Virtual Power Plant as a Service

VPPaaS is an energy management platform that ingests real-time telemetry from prosumer assets (batteries, solar panels, EV chargers), evaluates grid flexibility and balancing conditions through Camunda-orchestrated BPMN processes, and publishes aggregated analytics to downstream Kafka consumers. Eight independent Quarkus microservices communicate exclusively through a Kong API Gateway — no direct service-to-service HTTP calls exist in the system. Business logic lives in BPMN process definitions deployed to Camunda 8, with Zeebe service tasks making every inter-service call through Kong. The full stack — RDS, a 3-broker Kafka KRaft cluster, Camunda, Ollama, Kong, and all eight services — deploys to AWS across two accounts with a single script.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Microservices | Quarkus 3 · Java 17 |
| Reactive DB access | Vert.x MySQL client · SmallRye Mutiny |
| API documentation | SmallRye OpenAPI (Swagger UI) |
| Message streaming | Apache Kafka (3-node KRaft cluster) |
| Business process orchestration | Camunda BPMN 2.0 |
| API Gateway | Kong · Konga |
| AI / LLM | Ollama · llama3.2 |
| Database | MySQL 8 on Amazon RDS |
| Infrastructure | AWS EC2 · Terraform |
| Containerisation | Docker |
| Testing | Quarkus @QuarkusTest · REST-Assured · Mockito |

---

## Architecture Overview

```
                        ┌─────────────────────────────────────────────────┐
                        │              CLIENTS / BPMN ENGINE               │
                        │        (Camunda on t3.large EC2)                 │
                        └──────────────────────┬──────────────────────────┘
                                               │ HTTP
                                               ▼
                        ┌─────────────────────────────────────────────────┐
                        │            Kong API Gateway (port 8000)          │
                        │    9 services · 36 routes · Konga admin UI       │
                        └──┬──────┬──────┬──────┬──────┬──────┬──────┬───┘
                           │      │      │      │      │      │      │
              ┌────────────┘  ┌───┘  ┌───┘  ┌───┘  ┌───┘  ┌───┘  ┌─┘
              ▼               ▼      ▼      ▼      ▼      ▼      ▼
        ┌──────────┐  ┌──────────┐  ...  (8 Quarkus microservices, each on port 8080)
        │ Prosumer │  │UtilityOp │
        └────┬─────┘  └────┬─────┘
             │              │
             └──────┬───────┘
                    │ non-blocking SQL (Vert.x + Mutiny)
                    ▼
        ┌───────────────────────┐        ┌────────────────────────────┐
        │  MySQL 8 on RDS       │        │  Apache Kafka (3-node       │
        │  db.t4g.micro · 20GB  │        │  KRaft cluster, port 9092) │
        └───────────────────────┘        └────────────┬───────────────┘
                                                      │
                                         ┌────────────┴────────────┐
                                         │  VPPaaS Event Producer  │
                                         │  (Java simulator JAR)   │
                                         └─────────────────────────┘

        ┌───────────────────────────────────────────────────────────┐
        │  Ollama (t3.large, port 11434) — llama3.2:latest          │
        │  Called by FlexibilityForecasting for AI-assisted forecasts│
        └───────────────────────────────────────────────────────────┘
```

All infrastructure is provisioned on AWS (us-east-1) via Terraform. Data flow diagrams for each business process are in [`DataFlowDiagrams/`](DataFlowDiagrams/).

---

## The Eight Microservices

Each service is a Quarkus 3 application on Java 17 running on a dedicated t3.small EC2 instance. All database access is fully non-blocking, using the Vert.x reactive MySQL client with SmallRye Mutiny (`Uni<T>` / `Multi<T>`). Every service exposes its API via SmallRye OpenAPI.

| Service | Responsibility |
|---|---|
| **Prosumer** | Manages prosumer entities and their energy assets (BATTERY, SOLAR, EV_CHARGER) |
| **UtilityOperator** | Manages utility operators and their grid cells (geographic zones with capacity limits) |
| **AssetLink** | Associates prosumers with utility operators and triggers Kafka consumer provisioning |
| **Telemetry** | Consumes asset telemetry from Kafka, persists it, and exposes a read API |
| **FlexibilityEmission** | Applies business rules to battery telemetry and publishes flexibility offers |
| **FlexibilityForecasting** | Uses Ollama (llama3.2) to generate AI-assisted forecasts of future flexibility |
| **GridBalancing** | Detects overloaded grid cells and recommends neighbouring zones to absorb load |
| **EnergyAnalytics** | Computes four energy metrics (SoC, discharged/generated/consumed energy) and publishes to Kafka |

---

## Business Processes (Camunda BPMN)

All business logic is orchestrated by Camunda. The ten BPMN processes in [`code/BPMN/`](code/BPMN/) drive every microservice call via HTTP service tasks routed through Kong.

### Registration flows
- **ProsumerManagement** — Two-pool collaboration (Initiator + Executor) with Promise/Declare/Accept/Reject message correlation. The prosumer submits a creation request; a manager evaluates it and either promises to proceed or declines; the prosumer gets a final review before accepting.
- **CreateAsset** — Same two-pool pattern. Initiator selects from a live prosumer catalogue, picks an asset type (BATTERY, SOLAR, EV_CHARGER), and the Executor registers it.
- **UtilityOperatorManagement** — Single-pool, two-lane linear flow for registering utility operators.
- **CreateGridCell** — Registers a named geographic zone (e.g. `LISBON-RES-01`) under an existing utility operator, with postal-code-based neighbour resolution.
- **AssetLinkManagement** — Three-pool collaboration that links a prosumer to a utility operator _and_ provisions a dedicated Kafka consumer on the Telemetry service in parallel, via a parallel split gateway.

### Automated/timer-triggered flows
- **FlexibilityEventEmission** — Fires every 10 minutes. Loops over all battery assets in parallel, applies four rules (connection status, SoH, SoC peak-window, SoC depletion), and publishes accepted offers to MySQL and the `flexibility-offers-out` Kafka topic.
- **FlexibilityForecasting** — Fires every 15 minutes. For each historical flexibility offer, fetches live telemetry, builds a structured natural-language prompt, sends it to Ollama, and parses the JSON forecast response.
- **GridBalancingRecommendation** — Fires every 15 minutes. Detects capacity overloads in grid cells and recommends neighbouring zones using postal-code proximity.
- **EnergyAnalytics** — Fires every 15 minutes. Two independent parallel pipelines (battery + prosumer) compute Average SoC, Energy-Discharged-by-Zone, Generated-Energy-by-Prosumer, and Consumed-Energy-by-Prosumer.

---

## Kafka Data Flow

Kafka serves two distinct roles:

**Inbound (telemetry ingestion):** The VPPaaS Event Producer publishes raw telemetry to dynamically named topics following the convention `<AssetLinkID>-<UtilityOperatorName>`. Topics are provisioned at runtime by the AssetLink BPMN process, which instructs the Telemetry service to spawn a `DynamicTopicConsumer` thread for each new link.

**Outbound (processed results):** FlexibilityEmission, GridBalancing, and EnergyAnalytics publish to dedicated output channels:
- `flexibility-offers-out`
- `grid-balancing-recommendation-out`
- `Average-SoC`
- `Energy-Discharged-by-Zone`
- `Generated-Energy-by-Prosumer`
- `Consumed-Energy-by-Prosumer`

---

## Running Locally (Single Service)

Each microservice lives under `code/microservices/<ServiceName>/` and uses the Quarkus Maven plugin.

```bash
# Dev mode with live reload
cd code/microservices/Prosumer
./mvnw compile quarkus:dev

# Run tests
./mvnw test

# Build JAR
./mvnw package
```

Swagger UI is available at `http://localhost:8080/q/swagger-ui/` when any service is running.

---

## Full AWS Deployment

### Prerequisites

- Terraform
- Docker
- Java 17
- AWS CLI configured with valid credentials in `access.sh`

```bash
# access.sh — sourced by all scripts, never commit this file
use_account1() {
    export AWS_ACCESS_KEY_ID=<your-key>
    export AWS_SECRET_ACCESS_KEY=<your-secret>
    export AWS_SESSION_TOKEN=<your-token>
    export AWS_DEFAULT_REGION=us-east-1
}
```

### Deploy everything

```bash
# macOS
./code/DeploymentAutomation-macOS.sh

# Ubuntu
./code/DeploymentAutomation-ubuntu.sh
```

This single script:
1. Provisions all AWS infrastructure via Terraform (RDS, Kafka cluster, Camunda, Kong, all 8 Quarkus EC2 instances, Ollama)
2. Injects runtime config (DB host, Kafka brokers, Docker registry) into each service's `application.properties`
3. Builds and pushes Docker images for all eight microservices
4. Starts each service on its EC2 instance

### Tear down

```bash
./code/UndeploymentAutomation-all.sh
```

### Useful scripts

| Script | Purpose |
|---|---|
| `kongCommands.sh` | Registers all 9 Kong services and 36 routes after Kong is provisioned |
| `kongCommands-Invocation-Tests.sh` | Smoke-tests every Kong route with sample HTTP requests |
| `create-kafka-topics.sh` | Creates asset zone topics and registers Telemetry consumers via Kong |
| `check-kafka.sh <broker-dns>` | SSHes into a broker and reports topic list, message counts, and content |
| `clean-all-projects.sh` | Runs `mvn clean` on all services and removes `.terraform` directories |

---

## Event Producer (Telemetry Simulator)

The Event Producer simulates asset telemetry. It discovers all Kafka topics at startup and publishes randomised BATTERY, SOLAR_INVERTER, and EV_CHARGER messages. Requires Java 17.

```bash
java -jar VPPaaS-EventProducer/VPPaaSSimulator.jar \
  --broker-list <broker1>:9092,<broker2>:9092,<broker3>:9092 \
  --throughput 10 \
  --filterprefix "" \
  --kong-url http://<kong-host>:8000
```

| Flag | Description |
|---|---|
| `--broker-list` | Comma-separated list of Kafka brokers |
| `--throughput` | Approximate max messages per minute |
| `--filterprefix` | Only publish to topics starting with this prefix; `""` means all |
| `--kong-url` | Kong proxy base URL for consumer registration |

