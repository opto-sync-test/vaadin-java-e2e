package dev.optosync.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public final class MultiplexBackgroundWorker {
    public record Mutation(String lane, long sequence, String recordId, String json) {
    }

    public record Batch(String lane, List<Mutation> mutations) {
        public Batch {
            mutations = List.copyOf(mutations);
        }
    }

    public record LaneResult(
            String lane,
            long acknowledgedThrough,
            List<String> authoritativeJson,
            int attempts) {
        public LaneResult {
            authoritativeJson = List.copyOf(authoritativeJson);
        }
    }

    @FunctionalInterface
    public interface Transport {
        List<String> exchange(Batch batch) throws Exception;
    }

    private final Transport transport;
    private final int maxAttempts;

    public MultiplexBackgroundWorker(Transport transport) {
        this(transport, 3);
    }

    public MultiplexBackgroundWorker(Transport transport, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.transport = transport;
        this.maxAttempts = maxAttempts;
    }

    public List<Batch> snapshot(List<Mutation> pending) {
        var lanes = new LinkedHashMap<String, List<Mutation>>();
        pending.stream()
                .sorted(Comparator.comparing(Mutation::lane).thenComparingLong(Mutation::sequence))
                .forEach(mutation -> lanes.computeIfAbsent(mutation.lane(), ignored -> new ArrayList<>())
                        .add(mutation));
        return lanes.entrySet().stream()
                .map(entry -> new Batch(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<LaneResult> drain(List<Mutation> pending) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = snapshot(pending).stream()
                    .map(batch -> CompletableFuture.supplyAsync(() -> replay(batch), executor))
                    .toList();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparing(LaneResult::lane))
                    .toList();
        }
    }

    private LaneResult replay(Batch immutableBatch) {
        Exception failure = null;
        for (var attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var authoritative = transport.exchange(immutableBatch);
                var acknowledged = immutableBatch.mutations().getLast().sequence();
                return new LaneResult(immutableBatch.lane(), acknowledged, authoritative, attempt);
            } catch (Exception error) {
                failure = error;
            }
        }
        throw new IllegalStateException(
                "OptoSync lane " + immutableBatch.lane() + " exhausted its replay budget",
                failure);
    }
}
