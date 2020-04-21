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
 * @description: 连接已禁用：逻辑连接已禁用。
 * @date 2020/4/20
 */
public class ConnectionDeactivatedEventVisitor implements EventVisitor {
    private static final Logger log = LoggerFactory.getLogger(ConnectionDeactivatedEventVisitor.class);

    private final String name;
    private final MetricRegistry metrics;

    public ConnectionDeactivatedEventVisitor(final MetricRegistry metrics) {
        this.name = "event.connection-deactivated";
        this.metrics = requireNonNull(metrics);
    }

    public ConnectionDeactivatedEventVisitor(final String name, final MetricRegistry metrics) {
        this.name = MetricRegistry.name(requireNonNull(name), "event.connection-deactivated");
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
        // do nothing
    }

    @Override
    public void visit(final ConnectionDeactivatedEvent event) {
        metrics.counter(name).inc();
        log.debug("Connection deactivation event occurred: {}", event);
    }
}
