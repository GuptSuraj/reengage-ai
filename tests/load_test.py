#!/usr/bin/env python3
import argparse
import json
import math
import os
import platform
import statistics
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, math.ceil(len(ordered) * fraction) - 1)]


def send(url, events):
    started = time.perf_counter()
    request = Request(
        f"{url}/api/v1/events/batch",
        data=json.dumps({"events": events}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=15) as response:
        result = json.load(response)
    return time.perf_counter() - started, result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://localhost:8080")
    parser.add_argument("--events", type=int, default=10000)
    parser.add_argument("--batch-size", type=int, default=50)
    parser.add_argument("--concurrency", type=int, default=20)
    args = parser.parse_args()
    kinds = ["page_viewed", "search_performed", "product_viewed", "filter_applied", "product_compared", "add_to_cart"]
    all_events = [
        {
            "eventId": str(uuid.uuid4()), "anonymousId": f"load-user-{i % 500}",
            "sessionId": str(uuid.uuid5(uuid.NAMESPACE_URL, f"load-session-{i % 1000}")),
            "eventType": kinds[i % len(kinds)],
            "productId": "sony-wh-ch720n", "timestamp": datetime.now(timezone.utc).isoformat(),
            "sourcePage": "/load-test", "device": {"type": "desktop"}, "metadata": {},
        } for i in range(args.events)
    ]
    batches = [all_events[i:i + args.batch_size] for i in range(0, len(all_events), args.batch_size)]
    start = time.perf_counter()
    latencies, accepted, errors = [], 0, []
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(send, args.url, batch) for batch in batches]
        for future in as_completed(futures):
            try:
                latency, result = future.result()
                latencies.append(latency)
                accepted += result["accepted"]
            except Exception as exc:
                errors.append(str(exc))
    elapsed = time.perf_counter() - start
    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "platform": platform.platform(),
        "python": platform.python_version(),
        "configuration": vars(args),
        "acceptedEvents": accepted,
        "elapsedSeconds": round(elapsed, 3),
        "eventsPerSecond": round(accepted / elapsed, 2),
        "requestLatencyMs": {
            "p50": round(statistics.median(latencies) * 1000, 2) if latencies else None,
            "p95": round(percentile(latencies, .95) * 1000, 2) if latencies else None,
        },
        "errors": len(errors),
        "errorSamples": errors[:5],
    }
    Path("reports").mkdir(exist_ok=True)
    Path("reports/load-test-latest.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
