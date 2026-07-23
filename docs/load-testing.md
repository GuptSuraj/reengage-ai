# Load-testing method

`tests/load_test.py` targets the Spring ingestion endpoint with anonymous, schema-valid, batched events. It records only observed values.

Start the stack, allow services to become healthy, and run:

```bash
python3 tests/load_test.py \
  --url http://localhost:8080 \
  --events 10000 \
  --batch-size 50 \
  --concurrency 20
```

Before publishing a result, record:

- git commit and whether the working tree was clean;
- CPU, memory, OS, Docker resource allocation, and topology;
- event count, batch size, concurrency, Kafka partitions, and database settings;
- warm-up procedure and number of independent runs;
- accepted, duplicate, and rejected counts;
- P50/P95 request latency, throughput, and error rate;
- Kafka consumer lag and P50/P95 end-to-end processing latency.

Run a warm-up first, then at least five measured trials. Report the median trial and its variance rather than selecting the best run. Load generation should move to a second machine when testing networked production capacity.

The harness writes `reports/load-test-latest.json`. The file is ignored so measurements cannot be mistaken for portable project claims.
