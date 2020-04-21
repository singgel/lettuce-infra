# lettuce-infra
metric tracing log


问题：
线上的redis日志不是很全面，例如：

1.无法得知数据返回的remote address

2.没有主动记录slowlog慢查询日志

3.没有记录connection连接状态

4.没有集群的网络拓扑

分析：
按照官方提供的EventBus总线，异步事件流提供了metric指标信息

https://github.com/lettuce-io/lettuce-core/wiki/Connection-Events

根据官方的Collector事件采集，提供了address之类相关信息

https://github.com/lettuce-io/lettuce-core/wiki/Command-Latency-Metrics

解决：
根据以上相关帮助文档，采用适配器模式方便Event扩展，采用工厂模式将EventBus添加进消费序列