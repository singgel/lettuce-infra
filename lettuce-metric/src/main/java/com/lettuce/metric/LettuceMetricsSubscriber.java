package com.lettuce.metric;

import com.lettuce.metric.visitor.EventVisitor;
import com.lettuce.metric.wrapper.EventWrapperFactory;
import com.lettuce.metric.wrapper.VisitableEventWrapper;
import io.lettuce.core.event.Event;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author heks
 * @description: TODO
 * @date 2020/4/20
 */
public class LettuceMetricsSubscriber implements Consumer<Event> {
    private final List<EventVisitor> eventVisitors;

    public LettuceMetricsSubscriber(List<EventVisitor> eventVisitors) {
        this.eventVisitors = eventVisitors;
    }

    @Override
    public void accept(Event event) {
        final Optional<VisitableEventWrapper> eventWrapperOpt = EventWrapperFactory.build(event);
        eventWrapperOpt.ifPresent(eventWrapper -> eventVisitors.forEach(eventWrapper::accept));
    }
}
