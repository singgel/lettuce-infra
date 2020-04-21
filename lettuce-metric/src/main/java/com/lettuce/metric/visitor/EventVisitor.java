package com.lettuce.metric.visitor;

import io.lettuce.core.cluster.event.ClusterTopologyChangedEvent;
import io.lettuce.core.event.connection.ConnectedEvent;
import io.lettuce.core.event.connection.ConnectionActivatedEvent;
import io.lettuce.core.event.connection.ConnectionDeactivatedEvent;
import io.lettuce.core.event.connection.DisconnectedEvent;
import io.lettuce.core.event.metrics.CommandLatencyEvent;

/**
 * @author heks
 * @description: 统一规划处接口，便于接口的规范和后续的扩展
 * @date 2020/4/20
 */
public interface EventVisitor {
    void visit(CommandLatencyEvent event);

    void visit(DisconnectedEvent event);

    void visit(ClusterTopologyChangedEvent event);

    void visit(ConnectedEvent event);

    void visit(ConnectionActivatedEvent event);

    void visit(ConnectionDeactivatedEvent event);
}
