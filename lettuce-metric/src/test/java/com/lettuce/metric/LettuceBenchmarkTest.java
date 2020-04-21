package com.lettuce.metric;

/**
 * @author heks
 * @description: TODO
 * @date 2020/3/27
 */

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.lettuce.metric.visitor.*;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 1)
@Threads(200)
@State(Scope.Benchmark)
@Measurement(iterations = 2, time = 600, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class LettuceBenchmarkTest {
    private static final int LOOP = 1;
    private static final String clusterName = "localhost";
    private StatefulRedisConnection<String, String> connection;
    private MetricRegistry metrics;
    @Setup
    public void setup() {
        metrics = new MetricRegistry();
        RedisURI redisUri = RedisURI.builder()
                .withHost("localhost")
                .withPort(6379)
                .withTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .build();
        RedisClient client = RedisClient.create(redisUri);
        client.getResources()
                .eventBus()
                .get()
                .subscribe(new LettuceMetricsSubscriber(buildEventVisitors(metrics)));
        connection = client.connect();
        connection.sync().ping();
    }

    protected List<EventVisitor> buildEventVisitors(final MetricRegistry metrics) {
        // Extract this, and the event wrapper builders, to Dropwizard factories, if more event types are added frequently enough?
        return ImmutableList.of(
                new ClusterTopologyChangedEventVisitor(clusterName, metrics),
                new CommandLatencyEventVisitor(clusterName, metrics),
                new ConnectedEventVisitor(clusterName, metrics),
                new ConnectionActivatedEventVisitor(clusterName, metrics),
                new ConnectionDeactivatedEventVisitor(clusterName, metrics),
                new DisconnectedEventVisitor(clusterName, metrics)
        );
    }

    @Benchmark
    public void get() throws ExecutionException, InterruptedException {
        RedisCommands<String, String> commands = connection.sync();
        List<RedisFuture<String>> redisFutureList = new ArrayList<>();
        for (int i = 0; i < LOOP; ++i) {
            commands.get("a");
            /*RedisFuture<String> future = commands.get("a");
            redisFutureList.add(future);
            future.get();*/
        }
        /*redisFutureList.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });*/
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(LettuceBenchmarkTest.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(options).run();
    }
}
