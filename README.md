# ReEngageAI

ReEngageAI is an event-driven customer re-engagement platform and commerce demo. It records shopping behaviour, builds recency-weighted profiles, explains purchase intent, ranks relevant products, and schedules WhatsApp or email reminders only after deterministic consent, stock, frequency, and timing checks.

## Architecture

| Layer | Technology | Responsibility |
|---|---|---|
| Store and dashboard | Next.js 16, React 19, TypeScript | Authenticated commerce UI, analytics, secure API proxy |
| Core platform | Java 21, Spring Boot 4.1 | Auth, catalogue, cart, checkout, ingestion, decisions, scheduler, analytics |
| Intelligence | Python 3.12, FastAPI | Explainable intent scoring, behavioural profiles, hybrid ranking |
| Event backbone | Apache Kafka 4.3 | Behaviour and purchase streams, retries, dead-letter routing |
| State | PostgreSQL 17, Redis 8, Qdrant | Transactional state/outbox, delayed jobs/rate limits, vectors |
| Operations | OpenTelemetry, Prometheus, Grafana | Traces, metrics, health, dashboards |

The core is a modular monolith with an extracted AI service and Kafka consumers. This preserves transaction boundaries for checkout and notification cancellation while keeping event-processing and model workloads independently scalable.

## Implemented capabilities

- JWT authentication with BCrypt password hashes, role-based API access, HttpOnly browser sessions, validation, CORS, security headers, and Redis-backed rate limiting.
- Searchable/filterable catalogue, inventory, cart, idempotent checkout, order history, and row-level stock locking.
- Reusable TypeScript tracking SDK with typed events, asynchronous batching, retries, session/device context, and page-duration tracking.
- Idempotent ingestion, event/received timestamps, transactional outbox, Kafka partition keys, exponential consumer retries, and dead-letter topics.
- Recency-weighted profile and explainable 0–1 intent score with signal contributions.
- Hybrid recommendation ranking using product embeddings, category/brand/price preference, quality, popularity, and business exclusions.
- Deterministic notification eligibility, quiet hours, opt-outs, channel fallback, caps, duplicate suppression, expiration, purchase cancellation, and retry with jitter.
- PostgreSQL as canonical job state plus a Redis sorted-set scheduler with a repair sweep after Redis loss.
- Mock WhatsApp/email providers behind adapters, with attempts, failures, opens, clicks, and conversion data persisted.
- Admin dashboard for events, active/high-intent users, carts, queue state, delivery, CTR, conversion, variants, lag, and recent activity.
- Docker Compose, Kubernetes deployment templates, CI, health probes, Prometheus scraping, and OpenTelemetry collection.

## Quick start

Prerequisites: Docker Desktop with Compose v2, 8 GB RAM available, and ports `3000`, `3001`, `5432`, `6333`, `6379`, `8000`, `8080`, `9090`, and `29092` free.

```bash
cp .env.example .env
docker compose up --build
```

Wait until `core-api` is healthy, then open:

- Store: [http://localhost:3000](http://localhost:3000)
- Core health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- AI health: [http://localhost:8000/health/ready](http://localhost:8000/health/ready)
- Prometheus: [http://localhost:9090](http://localhost:9090)
- Grafana: [http://localhost:3001](http://localhost:3001)

Demo credentials:

```text
Customer: demo@reengage.ai / Demo@12345
Admin:    admin@reengage.ai / Admin@12345
Grafana:  admin / admin
```

Sign in as the customer to browse and check out. Sign in as the admin, open `/admin`, and select **Simulate high-intent journey**. The local notification delay is 15 seconds so the complete decision-to-delivery loop can be demonstrated quickly.

Stop without deleting data:

```bash
docker compose stop
```

Remove containers and local volumes:

```bash
docker compose down --volumes
```

## Development and verification

Docker is the supported full-stack environment. Individual services can also be run with Java 21, Maven 3.9+, Node 24, and Python 3.12:

```bash
make install
make test
make build
```

Useful commands:

```bash
make up              # build and start the platform
make logs            # follow application logs
make ps              # show service health
make smoke           # exercise auth, events, AI, cart, and checkout
make load            # measured ingestion test against localhost:8080
make down             # stop containers, preserve volumes
make clean            # stop and remove local volumes
```

No unmeasured throughput or model-accuracy number is claimed. The load harness records the machine, configuration, latency, throughput, and errors in `reports/load-test-latest.json`; publish résumé metrics only from a controlled run.

## Repository map

```text
apps/frontend/              Next.js storefront, BFF, and admin dashboard
services/core-api/          Spring Boot platform and Kafka consumers
services/ai-service/        FastAPI scoring/profile/recommendation service
packages/tracking-sdk/      Framework-independent TypeScript SDK
infra/k8s/                  Kubernetes deployment and scaling templates
infra/prometheus/           Metrics scrape configuration
infra/grafana/              Provisioned dashboard datasource
infra/otel/                 OpenTelemetry Collector configuration
docs/api.yaml               OpenAPI 3.1 contract
tests/load_test.py          Repeatable event-ingestion load harness
docker-compose.yml          Complete local topology
```

## Production boundary

The local adapters intentionally simulate WhatsApp and email delivery. Before a real deployment, configure approved WhatsApp Cloud API templates and a transactional email provider, place secrets in a secret manager, terminate TLS at the ingress, use managed multi-node data services, establish backups/retention, complete a privacy and security review, and run the supplied failure/load tests in the target environment.

An LLM may be added only after a notification is approved to rewrite its wording. Consent, eligibility, price, stock, discounts, timing, channel policy, and cancellation remain deterministic and auditable.

## Documentation

- [Architecture and reliability](docs/architecture.md)
- [OpenAPI contract](docs/api.yaml)
- [Load-testing method](docs/load-testing.md)
- [Load-test report status](docs/load-test-report.md)
- [Five-minute demo](docs/demo-script.md)
- [Kubernetes deployment](infra/k8s/README.md)
