#!/usr/bin/env python3
"""Run after loading performance-fixture.sql; measure real paginated HTTP responses."""
import json
from pathlib import Path
import statistics
import time
from acceptance_test import request, ROOT

samples = []
for i in range(30):
    started = time.perf_counter()
    data = request('GET', f'/goods?page={i % 20 + 1}&size=12&sort=newest')
    assert data['total'] >= 500, 'Load scripts/performance-fixture.sql first'
    assert len(data['items']) == 12, 'Pagination returned an incorrect page'
    samples.append((time.perf_counter()-started)*1000)
result = {'samples': len(samples), 'goods': data['total'], 'median_ms': round(statistics.median(samples), 2),
          'p95_ms': round(sorted(samples)[28], 2), 'max_ms': round(max(samples), 2),
          'target_ms': 3000, 'passed': max(samples) < 3000,
          'conditions': 'local HTTP / MySQL 8.4 / warmed JVM / single client / page size 12',
          'timestamp': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
(ROOT/'test-results').mkdir(exist_ok=True)
(ROOT/'test-results/performance-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
print(json.dumps(result, ensure_ascii=False, indent=2))
raise SystemExit(0 if result['passed'] else 1)
