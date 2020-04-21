package com.lettuce.metric.wrapper;


import com.lettuce.metric.visitor.EventVisitor;

/**
 * @author heks
 * @description: 提供统一的适配器，根据对应EventBus事件类型处理
 * @date 2020/4/20
 */
public interface VisitableEventWrapper {
    void accept(EventVisitor visitor);
}
