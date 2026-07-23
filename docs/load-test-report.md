# Load-test report

Status: **not executed against the complete Docker topology in this workspace**

The current host does not provide Docker, so a Kafka/PostgreSQL-backed ingestion result cannot be measured honestly here. Compilation, unit tests, type checking, and dependency audit results are documented in the repository handoff; they are not throughput benchmarks.

When Docker is available:

```bash
docker compose up --build -d
docker compose ps
python3 tests/load_test.py --events 10000 --batch-size 50 --concurrency 20
```

Attach `reports/load-test-latest.json`, the git commit, hardware, Docker allocation, Kafka partition count, and five-trial summary before using any latency or events-per-second value on a résumé.
