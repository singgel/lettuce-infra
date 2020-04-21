package com.lettuce.metric.wrapper;

import com.lettuce.metric.visitor.EventVisitor;
import io.lettuce.core.cluster.event.ClusterTopologyChangedEvent;

import static java.util.Objects.requireNonNull;

/**
 * @author heks
 * @description: TODO
 * @date 2020/4/20
 */
public class VisitableClusterTopologyEventWrapper implements VisitableEventWrapper {
    private final ClusterTopologyChangedEvent event;

    public VisitableClusterTopologyEventWrapper(final ClusterTopologyChangedEvent event) {
        this.event = requireNonNull(event);
    }

    @Override
    public void accept(final EventVisitor visitor) {
        visitor.visit(event);
    }
}
