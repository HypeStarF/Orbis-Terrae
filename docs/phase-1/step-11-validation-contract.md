# Step 11 validation contract

The dedicated benchmark is considered valid when:

- the deterministic workload fingerprint is identical across repeated runs;
- the sampled-result fingerprint is identical across repeated runs;
- warm nearest-elevation p95 is at or below 2,000,000 ns;
- both cache hits and misses are observed;
- no observed cache size exceeds four entries;
- the decoded elevation payload bound is exactly 512 bytes for the fixture;
- repeated four-thread workloads return the same checksum;
- the JSON report parses successfully.

Wall-clock latency values other than the warm target are recorded rather than treated as portable pass/fail
thresholds. File-system page caching, CPU scheduling, JIT compilation, and host hardware can change those
numbers without changing atlas correctness.
