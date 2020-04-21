package com.lettuce.metric.wrapper;

import com.lettuce.metric.visitor.EventVisitor;
import io.lettuce.core.event.connection.DisconnectedEvent;

import static java.util.Objects.requireNonNull;

/**
 * @author heks
 * @description: TODO
 * @date 2020/4/20
 */
public class VisitableDisconnectedEventWrapper implements VisitableEventWrapper {
    private final DisconnectedEvent event;

    public VisitableDisconnectedEventWrapper(final DisconnectedEvent event) {
        this.event = requireNonNull(event);
    }

    @Override
    public void accept(final EventVisitor visitor) {
        visitor.visit(event);
    }
}
