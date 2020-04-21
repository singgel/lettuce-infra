package com.lettuce.metric;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.lettuce.core.metrics.CommandLatencyCollector;
import io.lettuce.core.metrics.CommandLatencyId;
import io.lettuce.core.metrics.CommandMetrics;
import io.lettuce.core.protocol.ProtocolKeyword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author heks
 * @description: 重写lettuce的延时指标采集，用雪球的方式采集
 * @date 2020/4/20
 */
public class LettuceCommandLatencyCollector implements CommandLatencyCollector {
    private static final Logger log = LoggerFactory.getLogger(LettuceCommandLatencyCollector.class);

    private MetricRegistry metrics;

    public LettuceCommandLatencyCollector(MetricRegistry metrics) {
        this.metrics = metrics;
    }

    @Override
    public void recordCommandLatency(SocketAddress local, SocketAddress remote, ProtocolKeyword commandType, long firstResponseLatency, long completionLatency) {
        String tagFormate = commandType.name() + "." + remote.toString().substring(1).replace(".", "-");
        metrics.timer(tagFormate).update(firstResponseLatency, TimeUnit.NANOSECONDS);
        metrics.timer(tagFormate).update(completionLatency - firstResponseLatency, TimeUnit.NANOSECONDS);
        if (firstResponseLatency > 10000000 || completionLatency > 10000000) {
            log.warn("REDIS4 slowlog | " + local.toString() + " -> " + remote.toString() + " | " + commandType.name() + " | firstResponseLatency:" + firstResponseLatency + " ns | completionLatency:" + completionLatency + " ns");
        }
    }

    @Override
    public void shutdown() {
    }

    @Override
    public Map<CommandLatencyId, CommandMetrics> retrieveMetrics() {
        return ImmutableMap.of();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
