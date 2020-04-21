package com.lettuce.metric.wrapper;

import com.lettuce.metric.visitor.EventVisitor;
import io.lettuce.core.event.metrics.CommandLatencyEvent;

import static java.util.Objects.requireNonNull;

/**
 * @author heks
 * @description: TODO
 * @date 2020/4/20
 */
public class VisitableCommandLatencyEventWrapper implements VisitableEventWrapper {
    private final CommandLatencyEvent event;

    public VisitableCommandLatencyEventWrapper(final CommandLatencyEvent event) {
        this.event = requireNonNull(event);
    }

    @Override
    public void accept(final EventVisitor visitor) {
        visitor.visit(event);
    }
}
