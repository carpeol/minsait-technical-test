# Evolution roadmap

## Current behaviour and risks

The provided mocks exercise the most important upstream failure modes:

| Scenario | Mock behaviour | Effect without further controls |
|---|---|---|
| Normal | Product details return immediately | One request per similar product |
| Slow | Product `3` waits 100 ms; product `100` waits 1 s | Latency accumulates when details are fetched sequentially |
| Very slow | Product `1000` waits 5 s; product `10000` waits 50 s | Requests can occupy server threads and exceed caller deadlines |
| Not found | Product `5` returns `404` | A similar-product response can fail after some details were resolved |
| Upstream error | Product `6` returns `500` | Repeated calls can amplify an unhealthy dependency |

The K6 script applies 200 virtual users per scenario. The current implementation is deliberately simple and correct, but it should not be treated as the final production resilience profile.

## Resilience

1. **Configure separate connection, response, and overall request deadlines.**
   - Add properties for upstream connect timeout, read/response timeout, and the total use-case deadline.
   - Start with an end-to-end budget smaller than the public API timeout. A detail taking 50 s must fail quickly instead of retaining a request for 50 s.
   - Map upstream timeout and unavailable failures to an explicit `5xx` response without leaking implementation details.

2. **Apply Resilience4j at the outbound repository boundary.**
   - Use a circuit breaker per external product API operation.
   - Add a bounded bulkhead to prevent slow detail requests from exhausting all application resources.
   - Add a time limiter consistent with the request budget.
   - Retry only transient connection failures and selected `5xx` responses, with exponential backoff and jitter. Do not retry `404`, validation errors, or unboundedly retry a request.

3. **Define explicit degradation semantics.**
   - Decide whether one unavailable similar detail should fail the full response, omit that entry, or return a partial-result contract. The present contract has no partial-error representation, so failing the request is the safe current behaviour.
   - If partial results are required, evolve the OpenAPI contract first and make the decision observable in metrics and logs.

4. **Improve error handling at the inbound boundary.**
   - Return standard Problem Details for malformed product IDs and infrastructure errors.
   - Preserve `404` exclusively for a missing requested product, not for upstream failures.

## Performance and scalability

1. **Fetch similar product details concurrently with bounded concurrency.**
   - The IDs endpoint defines the required response order. Resolve details in parallel with a configured limit, then reassemble the output in input order.
   - Bounded concurrency is essential under the 200-VU K6 workload; unrestricted fan-out would overload the upstream service.
   - Use virtual threads or a dedicated, bounded executor after measuring their impact and propagating cancellation/deadlines.

2. **Cache product details when semantics allow it.**
   - A short, size-bounded cache for `GET /product/{id}` can eliminate repeated calls for popular IDs such as `100` and `1000`.
   - Cache only successful, immutable-enough details. Consider a short negative cache for `404` responses; never cache `500` or timeout results.
   - Define TTL, maximum size, eviction, and invalidation ownership with the upstream API team.

3. **Avoid duplicate resolution.**
   - The contract requires unique similar IDs, but treat duplicates defensively: resolve each ID once and preserve the first occurrence's position.
   - If `existsById` cannot use a cheaper upstream operation, it duplicates the product lookup. Evolve the upstream API with `HEAD /product/{id}` or an existence endpoint to avoid downloading an unused detail body.

4. **Tune the HTTP client.**
   - Configure connection-pool limits, pending-acquisition limits, idle eviction, keep-alive, and request timeouts.
   - Instrument pool saturation, connection acquisition duration, upstream request duration, and response status counts.
   - Size Tomcat/virtual-thread settings from load-test measurements rather than defaults.

## Observability and operations

1. **Extend metrics.**
   - Tag upstream requests by operation and result class (`2xx`, `404`, timeout, `5xx`), never by product ID to avoid high-cardinality series.
   - Publish circuit-breaker, bulkhead, retry, cache, and timeout metrics through Prometheus.
   - Add alerts for elevated error rate, open circuits, exhausted bulkheads, high upstream latency, and saturation.

2. **Add tracing.**
   - Propagate trace and correlation IDs to the external product API.
   - Emit structured logs with the trace ID, operation, outcome, duration, and upstream status. Avoid names or other customer data.

3. **Production readiness.**
   - Enable readiness and liveness probes separately; readiness should account for application initialization but should not make every upstream outage restart the service.
   - Use image vulnerability scanning, a non-root runtime user, resource requests/limits, and a graceful shutdown period in the deployment platform.

## Validation plan

Before adopting any change, add deterministic tests for timeout, circuit-open, bulkhead rejection, retry selection, cache hits/misses, ordering after concurrent retrieval, and cancellation. Then rerun the provided K6 scenarios and compare p50/p95/p99 latency, error rate, upstream call volume, and resource usage against a documented baseline.
