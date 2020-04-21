package com.lettuce.metric.wrapper;

import com.lettuce.metric.visitor.EventVisitor;
import io.lettuce.core.event.connection.ConnectionDeactivatedEvent;

import static java.util.Objects.requireNonNull;

/**
 * @author heks
 * @description: TODO
 * @date 2020/4/20
 */
public class VisitableConnectionDeactivatedEventWrapper implements VisitableEventWrapper {
    private final ConnectionDeactivatedEvent event;

    public VisitableConnectionDeactivatedEventWrapper(final ConnectionDeactivatedEvent event) {
        this.event = requireNonNull(event);
    }

    @Override
    public void accept(final EventVisitor visitor) {
        visitor.visit(event);
    }
}
