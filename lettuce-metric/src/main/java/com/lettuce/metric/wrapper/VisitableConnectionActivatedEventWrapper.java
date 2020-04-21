package com.lettuce.metric.wrapper;

import com.lettuce.metric.visitor.EventVisitor;
import io.lettuce.core.event.connection.ConnectionActivatedEvent;

import static java.util.Objects.requireNonNull;

/**
 * @author heks
 * @description: TODO
 * @date 2020/4/20
 */
public class VisitableConnectionActivatedEventWrapper implements VisitableEventWrapper {
    private final ConnectionActivatedEvent event;

    public VisitableConnectionActivatedEventWrapper(final ConnectionActivatedEvent event) {
        this.event = requireNonNull(event);
    }

    @Override
    public void accept(final EventVisitor visitor) {
        visitor.visit(event);
    }
}
