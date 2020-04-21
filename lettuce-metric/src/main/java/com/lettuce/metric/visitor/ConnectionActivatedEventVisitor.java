package com.lettuce.metric.visitor;

import com.codahale.metrics.MetricRegistry;
import io.lettuce.core.cluster.event.ClusterTopologyChangedEvent;
import io.lettuce.core.event.connection.ConnectedEvent;
import io.lettuce.core.event.connection.ConnectionActivatedEvent;
import io.lettuce.core.event.connection.ConnectionDeactivatedEvent;
import io.lettuce.core.event.connection.DisconnectedEvent;
import io.lettuce.core.event.metrics.CommandLatencyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * @author heks
 * @description: 连接已激活：逻辑连接已激活，可用于调度Redis命令（SSL握手已完成，在激活响应之前先进行PING）
 * @date 2020/4/20
 */
public class ConnectionActivatedEventVisitor implements EventVisitor {
    private static final Logger log = LoggerFactory.getLogger(ConnectionActivatedEventVisitor.class);

    private final String name;
    private final MetricRegistry metrics;

    public ConnectionActivatedEventVisitor(final MetricRegistry metrics) {
        this.name = "event.connection-activated";
        this.metrics = requireNonNull(metrics);
    }

    public ConnectionActivatedEventVisitor(final String name, final MetricRegistry metrics) {
        this.name = MetricRegistry.name(requireNonNull(name), "event.connection-activated");
        this.metrics = requireNonNull(metrics);
    }

    @Override
    public void visit(final CommandLatencyEvent event) {
        // do nothing
    }

    @Override
    public void visit(final DisconnectedEvent event) {
        // do nothing
    }

    @Override
    public void visit(final ClusterTopologyChangedEvent event) {
        // do nothing
    }

    @Override
    public void visit(final ConnectedEvent event) {
        // do nothing
    }

    @Override
    public void visit(final ConnectionActivatedEvent event) {
        metrics.counter(name).inc();
        log.debug("Connection activation event occurred: {}", event);
    }

    @Override
    public void visit(final ConnectionDeactivatedEvent event) {
        // do nothing
    }
}
