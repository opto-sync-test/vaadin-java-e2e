# Vaadin Java + OptoSync E2E

This Java 21 fixture combines Spring Boot 4.1, Vaadin 25.2, the official pinned
OptoSync Java client, a browser service worker, and a virtual-thread background
worker.

`MainView` is a real Vaadin route and PWA shell. Its frontend module registers
`opto-sync-sw.js`, which persists immutable mutations in IndexedDB, uses
Background Sync when available, multiplexes lanes concurrently, and posts
authoritative responses back to controlled windows. Failed fetches leave the
row durable for the next wake.

`MultiplexBackgroundWorker` is the desktop/server counterpart. It snapshots
each lane in sequence order, starts each lane on its own Java virtual thread,
retries a whole immutable batch after a transport failure, and returns the
authoritative payload. The test uses a real barrier to prove both lanes overlap
and verifies that retry receives the same `Batch` object, not a reconstructed
or partially acknowledged payload.

The live Spring controller exposes `POST /api/sync/{lane}` for the browser
worker. Maven compiles the official `OptoSyncClient.java` directly from
`vendor/opto-sync-clients`, whose gitlink and nested `syncer.c` gitlink are
checked against `opto-sync-pin.json` in CI.

## Run locally

```sh
git submodule update --init --recursive
mvn test
mvn package
node --check frontend/opto-sync-register.js
node --check src/main/resources/META-INF/resources/opto-sync-sw.js
```

GitHub Actions repeats the Java tests, packages the Spring/Vaadin application,
and validates both browser worker modules on Node 24.
