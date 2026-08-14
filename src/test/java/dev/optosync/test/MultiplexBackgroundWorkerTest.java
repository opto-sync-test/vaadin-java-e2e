package dev.optosync.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zedpkg.opto_sync.OptoSyncClient;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MultiplexBackgroundWorkerTest {
    @Test
    void virtualThreadsReplayImmutableLanesAfterReconnect() {
        var firstAttempts = new CyclicBarrier(2);
        var attempts = new ConcurrentHashMap<String, AtomicInteger>();
        var observed = new ConcurrentHashMap<String, CopyOnWriteArrayList<MultiplexBackgroundWorker.Batch>>();

        var worker = new MultiplexBackgroundWorker(batch -> {
            observed.computeIfAbsent(batch.lane(), ignored -> new CopyOnWriteArrayList<>()).add(batch);
            var attempt = attempts.computeIfAbsent(batch.lane(), ignored -> new AtomicInteger())
                    .incrementAndGet();
            if (attempt == 1) {
                try {
                    firstAttempts.await(2, TimeUnit.SECONDS);
                } catch (TimeoutException error) {
                    throw new AssertionError("virtual-thread lanes did not overlap", error);
                }
                throw new IOException("simulated offline transport");
            }
            return List.of("{\"lane\":\"" + batch.lane() + "\",\"state\":\"authoritative\"}");
        });

        var pending = List.of(
                new MultiplexBackgroundWorker.Mutation("desktop", 1, "d-1", "{}"),
                new MultiplexBackgroundWorker.Mutation("mobile", 1, "m-1", "{}"),
                new MultiplexBackgroundWorker.Mutation("desktop", 2, "d-2", "{}"),
                new MultiplexBackgroundWorker.Mutation("mobile", 2, "m-2", "{}"));

        var results = worker.drain(pending);
        assertEquals(List.of("desktop", "mobile"), results.stream().map(MultiplexBackgroundWorker.LaneResult::lane).toList());
        assertTrue(results.stream().allMatch(result -> result.attempts() == 2 && result.acknowledgedThrough() == 2));

        for (var lane : List.of("desktop", "mobile")) {
            var snapshots = observed.get(lane);
            assertEquals(2, snapshots.size());
            assertSame(snapshots.get(0), snapshots.get(1), "retry must reuse the exact immutable batch");
        }

        var officialClient = new OptoSyncClient(URI.create("https://sync.example.test"), "test-token");
        assertEquals("sync.example.test", officialClient.baseUri().getHost());
        assertEquals("test-token", officialClient.bearerToken());
    }
}
