# 问题现象  
## 问题描述
**2020-07-14** 某某服务在上线一个redis集群双写功能时遇见线上日志报错，报错内容如下  
```
10.10.165.24/logs/stdout.log-2020071420.gz:2020-07-14 20:49:07.495|ERROR|c.x.s.s.c.CommentController|09d358c22ea99bc9.09d358c22ea99bc9<:09d358c22ea99bc9|
io.lettuce.core.RedisCommandExecutionException: READONLY You can't write against a read only slave.
	at io.lettuce.core.ExceptionFactory.createExecutionException(ExceptionFactory.java:135)
	at io.lettuce.core.LettuceFutures.awaitOrCancel(LettuceFutures.java:122)
	at io.lettuce.core.cluster.ClusterFutureSyncInvocationHandler.handleInvocation(ClusterFutureSyncInvocationHandler.java:123)
	at io.lettuce.core.internal.AbstractInvocationHandler.invoke(AbstractInvocationHandler.java:80)
	at com.sun.proxy.$Proxy135.setex(Unknown Source)
	at com.xueqiu.infra.redis4.RedisClusterImpl.lambda$setex$164(RedisClusterImpl.java:1489)
	at com.xueqiu.infra.redis4.RedisClusterImpl$$Lambda$1422/1017847781.apply(Unknown Source)
	at com.xueqiu.infra.redis4.RedisClusterImpl.execute(RedisClusterImpl.java:526)
	at com.xueqiu.infra.redis4.RedisClusterImpl.executeTotal(RedisClusterImpl.java:491)
	at com.xueqiu.infra.redis4.RedisClusterImpl.setex(RedisClusterImpl.java:1489)
	at com.xueqiu.snowflake.status.count.CounterServiceImpl.doubleWrite(CounterServiceImpl.java:76)
	at com.xueqiu.snowflake.status.comment.service.CommentServiceImpl.getCommentLikeCount(CommentServiceImpl.java:1358)
	at com.xueqiu.snowflake.status.comment.service.CommentServiceImpl.loadFieldWhenQueryFromDb(CommentServiceImpl.java:211)
	at com.xueqiu.snowflake.status.comment.service.CommentServiceImpl.query(CommentServiceImpl.java:180)
	at com.xueqiu.snowflake.status.comment.service.CommentServiceImpl$$FastClassBySpringCGLIB$$94627fd2.invoke(<generated>)
	at org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:204)
	at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.invokeJoinpoint(CglibAopProxy.java:746)
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
	at org.springframework.aop.interceptor.ExposeInvocationInterceptor.invoke(ExposeInvocationInterceptor.java:92)
```
>**对报错内容统计:** 双写了总共1000多条，其中失败138条  
>另外一个比较玄学的问题，**22个docker节点，其中只有5台左右出现问题，而且数量和  docker ip都不是固定的，重启之后问题消失**  
这个是困扰我们的一般思考的时候最大的问题，也是导致排查困难的原因，后面都会一步步给出解释  
>**Slave-read-only:** https://redis.io/commands/readonly  
readonly命令的解释如下：READONLY告诉 Redis Cluster 从节点客户端愿意读取可能过时的数据并且对写请求不感兴趣。  
当连接处于只读模式，只有操作涉及到该从节点的主节点不服务的键时，集群将会发送一个重定向给客户端。这可能是因为：  
客户端发送一个有关这个从节点的主节点不服务哈希槽的命令。  
集群被重新配置（例如重新分片）并且从节点不在服务给定哈希槽的命令。  
意思已就是：**从节点默认不接受任何的读写命令，由客户端控制**  
## 相关资源
**gitlab:** 保密    
**rolling:** 保密   
**redis cluster nodes:** 10.10.30.9:25671,10.10.49.9:25672,10.10.25.2:25651,10.10.25.3:25652,10.10.50.7:25676,10.10.50.16:25677,10.10.26.3:25666,10.10.28.15:25667,10.10.28.2:25661,10.10.28.3:25662,10.10.28.3:25686,10.10.28.15:25687,10.10.27.2:25656,10.10.27.3:25657,10.10.25.3:25681,10.10.27.3:25682 
**redis-sdk版本:** 
```
<dependency>
    <groupId>com.xueqiu.infra.redis4</groupId>
    <artifactId>redis-cluster4</artifactId>
    <version>0.9.8</version>
</dependency>
```
**redis cluster server版本:** 
```
# Server
redis_version:4.0.10
redis_git_sha1:00000000
redis_git_dirty:0
redis_build_id:da6c26a0ca9ce3a9
redis_mode:cluster
os:Linux 4.4.0-178-generic x86_64
arch_bits:64
multiplexing_api:epoll
atomicvar_api:atomic-builtin
gcc_version:4.8.5
process_id:21554
```

# 排查过程  
## 复现+留痕
升级redis-sdk版本，完善日志  
**gitlab MR:** 保密  
```
<dependency>
    <groupId>com.xueqiu.infra.redis4</groupId>
    <artifactId>redis-cluster4</artifactId>
    <version>0.9.9</version>
</dependency>

关键代码: log.info("REDIS4 cluster topology: {}", statefulRedisClusterConnection.getPartitions());
```
降低snowflake-status的lettuce相关日志级别  
**gitlab MR:** 保密  
```
<logger name="io.lettuce.core" level="DEBUG" additivity="false">
	<appender-ref ref="REDIS-LOG"/>
</logger>
```
>这里日志的级别这么调整是中间经历过两次的线上debug级别，评估了日志量每天几十Mb，同时在线下进行了trace级别的测试，评估出trace是debug的1.5倍不到  
**这里要注意，线上的操作要十分谨慎，不要一个问题没解决又引来另外一个问题**  
## redis监控
**redis matrix:** 保密
redis cluster slave node monitor
```
/data/server/redis-cluster/16379/redis-4.0.10//bin/redis-cli -h 10.10.27.3 -p 25682 monitor
/data/server/redis-cluster/16379/redis-4.0.10//bin/redis-cli -h 10.10.49.9 -p 25672 monitor
/data/server/redis-cluster/16379/redis-4.0.10//bin/redis-cli -h 10.10.25.3 -p 25652 monitor
/data/server/redis-cluster/16379/redis-4.0.10//bin/redis-cli -h 10.10.50.16 -p 25677 monitor
/data/server/redis-cluster/16379/redis-4.0.10//bin/redis-cli -h 10.10.28.15 -p 25667 monitor
/data/server/redis-cluster/16379/redis-4.0.10//bin/redis-cli -h 10.10.28.3 -p 25662 monitor
/data/server/redis-cluster/16379/redis-4.0.10//bin/redis-cli -h 10.10.28.15 -p 25687 monitor
/data/server/redis-cluster/16379/redis-4.0.10//bin/redis-cli -h 10.10.27.3 -p 25657 monitor
```
> slave monitor只记录了成功的命令，由于readonly错误，没有被monitor到  
**这里留一个疑问**：*slave monitor监控到了master同步过来的写命令，那么这部分命令会不会被记录到QPS里？*  
## 日志分析
**2020-07-27** 盘后上线，TRACE级别日志
zgrep "setex error" */logs/stdout.log-2020072717.gz |awk '{print $1}' |uniq -c
```
     29 10.10.160.25/logs/stdout.log-2020072717.gz:2020-07-27
     39 10.10.160.26/logs/stdout.log-2020072717.gz:2020-07-27
     16 10.10.165.22/logs/stdout.log-2020072717.gz:2020-07-27
      6 10.10.165.24/logs/stdout.log-2020072717.gz:2020-07-27
      2 10.10.54.22/logs/stdout.log-2020072717.gz:2020-07-27
     29 10.10.54.27/logs/stdout.log-2020072717.gz:2020-07-27
      3 10.10.54.28/logs/stdout.log-2020072717.gz:2020-07-27
```
zgrep "setex error" */logs/stdout.log-2020072718.gz |awk '{print $1}' |uniq -c
```
     36 10.10.160.25/logs/stdout.log-2020072718.gz:2020-07-27
     32 10.10.160.26/logs/stdout.log-2020072718.gz:2020-07-27
      6 10.10.165.24/logs/stdout.log-2020072718.gz:2020-07-27
      7 10.10.54.28/logs/stdout.log-2020072718.gz:2020-07-27
```
zgrep "setex error" */logs/stdout.log-2020072719.gz |awk '{print $1}' |uniq -c
```
     26 10.10.160.25/logs/stdout.log-2020072719.gz:2020-07-27
     30 10.10.160.26/logs/stdout.log-2020072719.gz:2020-07-27
      4 10.10.165.24/logs/stdout.log-2020072719.gz:2020-07-27
      6 10.10.54.28/logs/stdout.log-2020072719.gz:2020-07-27
```
zgrep "setex error" */logs/stdout.log-2020072720.gz |awk '{print $1}' |uniq -c
```
      1 10.10.160.25/logs/stdout.log-2020072720.gz:2020-07-27
```
zgrep "REDIS4 cluster topology" 10.10.160.25/logs/redis4_info.log-2020072717.gz |awk -F"|" '{print $5}' |uniq -c
```
10.10.160.25
4 REDIS4 cluster topology: Partitions [RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25686], nodeId='6a9ea568d6b49360afbb650c712bd7920403ba19', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841127000, configEpoch=14, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25652], nodeId='f5148dba1127bd9bada8ecc39341a0b72ef25d8e', connected=true, slaveOf='79cb673db12199c32737b959cd82ec9963106558', pingSentTimestamp=0, pongReceivedTimestamp=1595841125000, configEpoch=18, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25681], nodeId='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841127046, configEpoch=0, flags=[MASTER], aliases=[], slot count=227], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25662], nodeId='5e9d0c185a2ba2fc9564495730c874bea76c15fa', connected=true, slaveOf='2281f330d771ee682221bc6c239afd68e6f20571', pingSentTimestamp=0, pongReceivedTimestamp=1595841117000, configEpoch=5, flags=[MYSELF, SLAVE], aliases=[RedisURI [host='10.10.28.3', port=25662]], slot count=1821], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.2', port=25651], nodeId='79cb673db12199c32737b959cd82ec9963106558', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841127000, configEpoch=18, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.30.9', port=25671], nodeId='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841128046, configEpoch=6, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.2', port=25661], nodeId='2281f330d771ee682221bc6c239afd68e6f20571', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841123000, configEpoch=15, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25657], nodeId='f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305', connected=true, slaveOf='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', pingSentTimestamp=0, pongReceivedTimestamp=1595841127000, configEpoch=13, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25687], nodeId='f54cfebc12c69725f471d16133e7ca3a8567dc18', connected=true, slaveOf='6a9ea568d6b49360afbb650c712bd7920403ba19', pingSentTimestamp=0, pongReceivedTimestamp=1595841130051, configEpoch=14, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.26.3', port=25666], nodeId='f6788b4829e601642ed4139548153830c430b932', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841126000, configEpoch=16, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.2', port=25656], nodeId='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841127000, configEpoch=13, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25667], nodeId='f09ad21effff245cae23c024a8a886f883634f5c', connected=true, slaveOf='f6788b4829e601642ed4139548153830c430b932', pingSentTimestamp=0, pongReceivedTimestamp=1595841129048, configEpoch=16, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25682], nodeId='068e3bc73c27782c49782d30b66aa8b1140666ce', connected=true, slaveOf='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', pingSentTimestamp=0, pongReceivedTimestamp=1595841126044, configEpoch=12, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.49.9', port=25672], nodeId='e8b0311aeec4e3d285028abc377f0c277f9a5c74', connected=true, slaveOf='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', pingSentTimestamp=0, pongReceivedTimestamp=1595841125042, configEpoch=6, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.7', port=25676], nodeId='5f677e012808b09c67316f6ac5bdf0ec005cd598', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841128000, configEpoch=17, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.16', port=25677], nodeId='19c57214e4293b2e37d881534dcd55318fa96a70', connected=true, slaveOf='5f677e012808b09c67316f6ac5bdf0ec005cd598', pingSentTimestamp=0, pongReceivedTimestamp=1595841125000, configEpoch=17, flags=[SLAVE], aliases=[]]]
10.10.160.26
4 REDIS4 cluster topology: Partitions [RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.2', port=25651], nodeId='79cb673db12199c32737b959cd82ec9963106558', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841162105, configEpoch=18, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25652], nodeId='f5148dba1127bd9bada8ecc39341a0b72ef25d8e', connected=true, slaveOf='79cb673db12199c32737b959cd82ec9963106558', pingSentTimestamp=0, pongReceivedTimestamp=1595841160000, configEpoch=18, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.49.9', port=25672], nodeId='e8b0311aeec4e3d285028abc377f0c277f9a5c74', connected=true, slaveOf='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', pingSentTimestamp=0, pongReceivedTimestamp=1595841158000, configEpoch=6, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.2', port=25656], nodeId='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841162000, configEpoch=13, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25681], nodeId='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841155000, configEpoch=0, flags=[MASTER], aliases=[], slot count=227], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25687], nodeId='f54cfebc12c69725f471d16133e7ca3a8567dc18', connected=true, slaveOf='6a9ea568d6b49360afbb650c712bd7920403ba19', pingSentTimestamp=0, pongReceivedTimestamp=1595841164109, configEpoch=14, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25657], nodeId='f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305', connected=true, slaveOf='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', pingSentTimestamp=0, pongReceivedTimestamp=1595841162000, configEpoch=13, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25662], nodeId='5e9d0c185a2ba2fc9564495730c874bea76c15fa', connected=true, slaveOf='2281f330d771ee682221bc6c239afd68e6f20571', pingSentTimestamp=0, pongReceivedTimestamp=1595841158000, configEpoch=5, flags=[MYSELF, SLAVE], aliases=[RedisURI [host='10.10.28.3', port=25662]], slot count=1821], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.7', port=25676], nodeId='5f677e012808b09c67316f6ac5bdf0ec005cd598', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841165110, configEpoch=17, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.26.3', port=25666], nodeId='f6788b4829e601642ed4139548153830c430b932', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841166112, configEpoch=16, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25667], nodeId='f09ad21effff245cae23c024a8a886f883634f5c', connected=true, slaveOf='f6788b4829e601642ed4139548153830c430b932', pingSentTimestamp=0, pongReceivedTimestamp=1595841153090, configEpoch=16, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25686], nodeId='6a9ea568d6b49360afbb650c712bd7920403ba19', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841157000, configEpoch=14, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.30.9', port=25671], nodeId='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841162000, configEpoch=6, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25682], nodeId='068e3bc73c27782c49782d30b66aa8b1140666ce', connected=true, slaveOf='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', pingSentTimestamp=0, pongReceivedTimestamp=1595841163000, configEpoch=12, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.16', port=25677], nodeId='19c57214e4293b2e37d881534dcd55318fa96a70', connected=true, slaveOf='5f677e012808b09c67316f6ac5bdf0ec005cd598', pingSentTimestamp=0, pongReceivedTimestamp=1595841163107, configEpoch=17, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.2', port=25661], nodeId='2281f330d771ee682221bc6c239afd68e6f20571', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841162000, configEpoch=15, flags=[MASTER], aliases=[], slot count=2048]]
10.10.165.22
4 REDIS4 cluster topology: Partitions [RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.7', port=25676], nodeId='5f677e012808b09c67316f6ac5bdf0ec005cd598', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841359196, configEpoch=17, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.16', port=25677], nodeId='19c57214e4293b2e37d881534dcd55318fa96a70', connected=true, slaveOf='5f677e012808b09c67316f6ac5bdf0ec005cd598', pingSentTimestamp=0, pongReceivedTimestamp=1595841356000, configEpoch=17, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.49.9', port=25672], nodeId='e8b0311aeec4e3d285028abc377f0c277f9a5c74', connected=true, slaveOf='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', pingSentTimestamp=0, pongReceivedTimestamp=1595841359000, configEpoch=6, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25686], nodeId='6a9ea568d6b49360afbb650c712bd7920403ba19', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841358000, configEpoch=14, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25662], nodeId='5e9d0c185a2ba2fc9564495730c874bea76c15fa', connected=true, slaveOf='2281f330d771ee682221bc6c239afd68e6f20571', pingSentTimestamp=0, pongReceivedTimestamp=1595841360198, configEpoch=15, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.2', port=25651], nodeId='79cb673db12199c32737b959cd82ec9963106558', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841361200, configEpoch=18, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.30.9', port=25671], nodeId='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841353000, configEpoch=6, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.2', port=25661], nodeId='2281f330d771ee682221bc6c239afd68e6f20571', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841353000, configEpoch=15, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25682], nodeId='068e3bc73c27782c49782d30b66aa8b1140666ce', connected=true, slaveOf='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', pingSentTimestamp=0, pongReceivedTimestamp=1595841355000, configEpoch=12, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25681], nodeId='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841356190, configEpoch=0, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25687], nodeId='f54cfebc12c69725f471d16133e7ca3a8567dc18', connected=true, slaveOf='6a9ea568d6b49360afbb650c712bd7920403ba19', pingSentTimestamp=0, pongReceivedTimestamp=1595841355000, configEpoch=14, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25652], nodeId='f5148dba1127bd9bada8ecc39341a0b72ef25d8e', connected=true, slaveOf='79cb673db12199c32737b959cd82ec9963106558', pingSentTimestamp=0, pongReceivedTimestamp=1595841353000, configEpoch=18, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25667], nodeId='f09ad21effff245cae23c024a8a886f883634f5c', connected=true, slaveOf='f6788b4829e601642ed4139548153830c430b932', pingSentTimestamp=0, pongReceivedTimestamp=1595841357000, configEpoch=1, flags=[MYSELF, SLAVE], aliases=[RedisURI [host='10.10.28.15', port=25667]]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.26.3', port=25666], nodeId='f6788b4829e601642ed4139548153830c430b932', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841358194, configEpoch=16, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.2', port=25656], nodeId='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841356000, configEpoch=13, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25657], nodeId='f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305', connected=true, slaveOf='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', pingSentTimestamp=0, pongReceivedTimestamp=1595841357193, configEpoch=13, flags=[SLAVE], aliases=[]]]
10.10.165.24
4 REDIS4 cluster topology: Partitions [RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.7', port=25676], nodeId='5f677e012808b09c67316f6ac5bdf0ec005cd598', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841437000, configEpoch=17, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.49.9', port=25672], nodeId='e8b0311aeec4e3d285028abc377f0c277f9a5c74', connected=true, slaveOf='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', pingSentTimestamp=0, pongReceivedTimestamp=1595841435000, configEpoch=4, flags=[MYSELF, SLAVE], aliases=[RedisURI [host='10.10.49.9', port=25672]], slot count=227], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.16', port=25677], nodeId='19c57214e4293b2e37d881534dcd55318fa96a70', connected=true, slaveOf='5f677e012808b09c67316f6ac5bdf0ec005cd598', pingSentTimestamp=0, pongReceivedTimestamp=1595841444000, configEpoch=17, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25686], nodeId='6a9ea568d6b49360afbb650c712bd7920403ba19', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841440016, configEpoch=14, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.2', port=25651], nodeId='79cb673db12199c32737b959cd82ec9963106558', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841438000, configEpoch=18, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.30.9', port=25671], nodeId='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841443021, configEpoch=6, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25652], nodeId='f5148dba1127bd9bada8ecc39341a0b72ef25d8e', connected=true, slaveOf='79cb673db12199c32737b959cd82ec9963106558', pingSentTimestamp=0, pongReceivedTimestamp=1595841446024, configEpoch=18, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25687], nodeId='f54cfebc12c69725f471d16133e7ca3a8567dc18', connected=true, slaveOf='6a9ea568d6b49360afbb650c712bd7920403ba19', pingSentTimestamp=0, pongReceivedTimestamp=1595841442000, configEpoch=14, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25662], nodeId='5e9d0c185a2ba2fc9564495730c874bea76c15fa', connected=true, slaveOf='2281f330d771ee682221bc6c239afd68e6f20571', pingSentTimestamp=0, pongReceivedTimestamp=1595841442018, configEpoch=15, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.2', port=25661], nodeId='2281f330d771ee682221bc6c239afd68e6f20571', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841442000, configEpoch=15, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25681], nodeId='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841443000, configEpoch=0, flags=[MASTER], aliases=[], slot count=1821], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.2', port=25656], nodeId='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841444021, configEpoch=13, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.26.3', port=25666], nodeId='f6788b4829e601642ed4139548153830c430b932', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841440000, configEpoch=16, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25657], nodeId='f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305', connected=true, slaveOf='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', pingSentTimestamp=0, pongReceivedTimestamp=1595841440000, configEpoch=13, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25667], nodeId='f09ad21effff245cae23c024a8a886f883634f5c', connected=true, slaveOf='f6788b4829e601642ed4139548153830c430b932', pingSentTimestamp=0, pongReceivedTimestamp=1595841445022, configEpoch=16, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25682], nodeId='068e3bc73c27782c49782d30b66aa8b1140666ce', connected=true, slaveOf='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', pingSentTimestamp=0, pongReceivedTimestamp=1595841441018, configEpoch=12, flags=[SLAVE], aliases=[]]]
10.10.54.22
4 REDIS4 cluster topology: Partitions [RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.30.9', port=25671], nodeId='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841556000, configEpoch=6, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25686], nodeId='6a9ea568d6b49360afbb650c712bd7920403ba19', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841555000, configEpoch=14, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25662], nodeId='5e9d0c185a2ba2fc9564495730c874bea76c15fa', connected=true, slaveOf='2281f330d771ee682221bc6c239afd68e6f20571', pingSentTimestamp=0, pongReceivedTimestamp=1595841552000, configEpoch=15, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.2', port=25651], nodeId='79cb673db12199c32737b959cd82ec9963106558', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841557000, configEpoch=18, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.2', port=25661], nodeId='2281f330d771ee682221bc6c239afd68e6f20571', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841560131, configEpoch=15, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25687], nodeId='f54cfebc12c69725f471d16133e7ca3a8567dc18', connected=true, slaveOf='6a9ea568d6b49360afbb650c712bd7920403ba19', pingSentTimestamp=0, pongReceivedTimestamp=1595841558000, configEpoch=14, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.7', port=25676], nodeId='5f677e012808b09c67316f6ac5bdf0ec005cd598', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841551000, configEpoch=17, flags=[MYSELF, MASTER], aliases=[RedisURI [host='10.10.50.7', port=25676]], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.49.9', port=25672], nodeId='e8b0311aeec4e3d285028abc377f0c277f9a5c74', connected=true, slaveOf='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', pingSentTimestamp=0, pongReceivedTimestamp=1595841556125, configEpoch=6, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.16', port=25677], nodeId='19c57214e4293b2e37d881534dcd55318fa96a70', connected=true, slaveOf='5f677e012808b09c67316f6ac5bdf0ec005cd598', pingSentTimestamp=0, pongReceivedTimestamp=1595841557127, configEpoch=17, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25657], nodeId='f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305', connected=true, slaveOf='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', pingSentTimestamp=0, pongReceivedTimestamp=1595841557000, configEpoch=13, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25667], nodeId='f09ad21effff245cae23c024a8a886f883634f5c', connected=true, slaveOf='f6788b4829e601642ed4139548153830c430b932', pingSentTimestamp=0, pongReceivedTimestamp=1595841552000, configEpoch=16, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25681], nodeId='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841554000, configEpoch=0, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25682], nodeId='068e3bc73c27782c49782d30b66aa8b1140666ce', connected=true, slaveOf='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', pingSentTimestamp=0, pongReceivedTimestamp=1595841554000, configEpoch=12, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.26.3', port=25666], nodeId='f6788b4829e601642ed4139548153830c430b932', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841556000, configEpoch=16, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.2', port=25656], nodeId='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841559130, configEpoch=13, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25652], nodeId='f5148dba1127bd9bada8ecc39341a0b72ef25d8e', connected=true, slaveOf='79cb673db12199c32737b959cd82ec9963106558', pingSentTimestamp=0, pongReceivedTimestamp=1595841558128, configEpoch=18, flags=[SLAVE], aliases=[]]]
10.10.54.27
4 REDIS4 cluster topology: Partitions [RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.2', port=25651], nodeId='79cb673db12199c32737b959cd82ec9963106558', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841754000, configEpoch=18, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.26.3', port=25666], nodeId='f6788b4829e601642ed4139548153830c430b932', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841761838, configEpoch=16, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.2', port=25656], nodeId='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841762000, configEpoch=13, flags=[MYSELF, MASTER], aliases=[RedisURI [host='10.10.27.2', port=25656]], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25681], nodeId='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841760837, configEpoch=0, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25682], nodeId='068e3bc73c27782c49782d30b66aa8b1140666ce', connected=true, slaveOf='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', pingSentTimestamp=0, pongReceivedTimestamp=1595841761000, configEpoch=12, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.2', port=25661], nodeId='2281f330d771ee682221bc6c239afd68e6f20571', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841763841, configEpoch=15, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25657], nodeId='f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305', connected=true, slaveOf='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', pingSentTimestamp=0, pongReceivedTimestamp=1595841760000, configEpoch=13, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.16', port=25677], nodeId='19c57214e4293b2e37d881534dcd55318fa96a70', connected=true, slaveOf='5f677e012808b09c67316f6ac5bdf0ec005cd598', pingSentTimestamp=0, pongReceivedTimestamp=1595841759000, configEpoch=17, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25652], nodeId='f5148dba1127bd9bada8ecc39341a0b72ef25d8e', connected=true, slaveOf='79cb673db12199c32737b959cd82ec9963106558', pingSentTimestamp=0, pongReceivedTimestamp=1595841762840, configEpoch=18, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.7', port=25676], nodeId='5f677e012808b09c67316f6ac5bdf0ec005cd598', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841764844, configEpoch=17, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25686], nodeId='6a9ea568d6b49360afbb650c712bd7920403ba19', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841759836, configEpoch=14, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25667], nodeId='f09ad21effff245cae23c024a8a886f883634f5c', connected=true, slaveOf='f6788b4829e601642ed4139548153830c430b932', pingSentTimestamp=0, pongReceivedTimestamp=1595841759000, configEpoch=16, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.30.9', port=25671], nodeId='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841758000, configEpoch=6, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25687], nodeId='f54cfebc12c69725f471d16133e7ca3a8567dc18', connected=true, slaveOf='6a9ea568d6b49360afbb650c712bd7920403ba19', pingSentTimestamp=0, pongReceivedTimestamp=1595841756000, configEpoch=14, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25662], nodeId='5e9d0c185a2ba2fc9564495730c874bea76c15fa', connected=true, slaveOf='2281f330d771ee682221bc6c239afd68e6f20571', pingSentTimestamp=0, pongReceivedTimestamp=1595841761000, configEpoch=15, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.49.9', port=25672], nodeId='e8b0311aeec4e3d285028abc377f0c277f9a5c74', connected=true, slaveOf='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', pingSentTimestamp=0, pongReceivedTimestamp=1595841757000, configEpoch=6, flags=[SLAVE], aliases=[]]]
10.10.54.28
4 REDIS4 cluster topology: Partitions [RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.2', port=25651], nodeId='79cb673db12199c32737b959cd82ec9963106558', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841798000, configEpoch=18, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.30.9', port=25671], nodeId='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841797000, configEpoch=6, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.49.9', port=25672], nodeId='e8b0311aeec4e3d285028abc377f0c277f9a5c74', connected=true, slaveOf='d8b4f99e0f9961f2e866b92e7351760faa3e0f2b', pingSentTimestamp=0, pongReceivedTimestamp=1595841801000, configEpoch=4, flags=[MYSELF, SLAVE], aliases=[RedisURI [host='10.10.49.9', port=25672]], slot count=227], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.16', port=25677], nodeId='19c57214e4293b2e37d881534dcd55318fa96a70', connected=true, slaveOf='5f677e012808b09c67316f6ac5bdf0ec005cd598', pingSentTimestamp=0, pongReceivedTimestamp=1595841801000, configEpoch=17, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25667], nodeId='f09ad21effff245cae23c024a8a886f883634f5c', connected=true, slaveOf='f6788b4829e601642ed4139548153830c430b932', pingSentTimestamp=0, pongReceivedTimestamp=1595841804559, configEpoch=16, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25662], nodeId='5e9d0c185a2ba2fc9564495730c874bea76c15fa', connected=true, slaveOf='2281f330d771ee682221bc6c239afd68e6f20571', pingSentTimestamp=0, pongReceivedTimestamp=1595841802556, configEpoch=15, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.50.7', port=25676], nodeId='5f677e012808b09c67316f6ac5bdf0ec005cd598', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841798000, configEpoch=17, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.2', port=25661], nodeId='2281f330d771ee682221bc6c239afd68e6f20571', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841800553, configEpoch=15, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.2', port=25656], nodeId='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841799000, configEpoch=13, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25652], nodeId='f5148dba1127bd9bada8ecc39341a0b72ef25d8e', connected=true, slaveOf='79cb673db12199c32737b959cd82ec9963106558', pingSentTimestamp=0, pongReceivedTimestamp=1595841797549, configEpoch=18, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.25.3', port=25681], nodeId='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841803557, configEpoch=0, flags=[MASTER], aliases=[], slot count=1821], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.26.3', port=25666], nodeId='f6788b4829e601642ed4139548153830c430b932', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841799551, configEpoch=16, flags=[MASTER], aliases=[], slot count=2048], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25657], nodeId='f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305', connected=true, slaveOf='5a12dd423370e6f4085e593f9cd0b3a4ddfa9757', pingSentTimestamp=0, pongReceivedTimestamp=1595841798000, configEpoch=13, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.27.3', port=25682], nodeId='068e3bc73c27782c49782d30b66aa8b1140666ce', connected=true, slaveOf='ff5f5a56a7866f32e84ec89482aabd9ca1f05e20', pingSentTimestamp=0, pongReceivedTimestamp=1595841801555, configEpoch=12, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.15', port=25687], nodeId='f54cfebc12c69725f471d16133e7ca3a8567dc18', connected=true, slaveOf='6a9ea568d6b49360afbb650c712bd7920403ba19', pingSentTimestamp=0, pongReceivedTimestamp=1595841794546, configEpoch=14, flags=[SLAVE], aliases=[]], RedisClusterNodeSnapshot [uri=RedisURI [host='10.10.28.3', port=25686], nodeId='6a9ea568d6b49360afbb650c712bd7920403ba19', connected=true, slaveOf='null', pingSentTimestamp=0, pongReceivedTimestamp=1595841798550, configEpoch=14, flags=[MASTER], aliases=[], slot count=2048]]
```
zgrep 06d1c3307a0d72b6 10.10.160.25/logs/redis4_info.log-2020072717.gz
```
10.10.160.25
0b559a2fc6aca560.0b559a2fc6aca560<:0b559a2fc6aca560|[channel=0xb8e6c429, /10.10.160.25:39454 -> /10.10.28.3:25662, epid=0x1f]
9f63122ae380eacd.9f63122ae380eacd<:9f63122ae380eacd|[channel=0xb8e6c429, /10.10.160.25:39454 -> /10.10.28.3:25662, epid=0x1f]
06d1c3307a0d72b6.06d1c3307a0d72b6<:06d1c3307a0d72b6|Connecting to Redis at 10.10.28.3:25662
10.10.160.26
016f6536e33bcee0.016f6536e33bcee0<:016f6536e33bcee0|[channel=0x7d71cfdd, /10.10.160.26:51660 -> /10.10.28.3:25662, epid=0x1a]
45ed29c8410e7a2b.45ed29c8410e7a2b<:45ed29c8410e7a2b|[channel=0x7d71cfdd, /10.10.160.26:51660 -> /10.10.28.3:25662, epid=0x1a]
ae40d7c96d7bd246.ae40d7c96d7bd246<:ae40d7c96d7bd246|[channel=0x7d71cfdd, /10.10.160.26:51660 -> /10.10.28.3:25662, epid=0x1a]
10.10.165.22
28c570df5ed7ed9a.28c570df5ed7ed9a<:28c570df5ed7ed9a|[channel=0x976ec557, /10.10.165.22:53380 -> /10.10.28.3:25662, epid=0x1a]
14059efcc606013e.14059efcc606013e<:14059efcc606013e|[channel=0x976ec557, /10.10.165.22:53380 -> /10.10.28.3:25662, epid=0x1a]
10.10.165.24
cc3a224471db35d2.cc3a224471db35d2<:cc3a224471db35d2|[channel=0x7695b210, /10.10.165.24:34587 -> /10.10.49.9:25672, epid=0x1f]
2d243226ad89cd61.2d243226ad89cd61<:2d243226ad89cd61|Connecting to Redis at 10.10.49.9:25672
10.10.54.22
5aa1c3dd8aafd1e9.5aa1c3dd8aafd1e9<:5aa1c3dd8aafd1e9|[channel=0x3c86aa04, /10.10.54.22:55070 -> /10.10.49.9:25672, epid=0x1f]
1775e13b7b6108c1.1775e13b7b6108c1<:1775e13b7b6108c1|[channel=0x3c86aa04, /10.10.54.22:55070 -> /10.10.49.9:25672, epid=0x1f]
10.10.54.27
22a67226cd0765c5.22a67226cd0765c5<:22a67226cd0765c5|[channel=0xb030641f, /10.10.54.27:48451 -> /10.10.28.3:25662, epid=0x1e]
180d6b1460565f52.180d6b1460565f52<:180d6b1460565f52|[channel=0xb030641f, /10.10.54.27:48451 -> /10.10.28.3:25662, epid=0x1e]
4fcabcbad7b89b96.4fcabcbad7b89b96<:4fcabcbad7b89b96|[channel=0xb030641f, /10.10.54.27:48451 -> /10.10.28.3:25662, epid=0x1e]
0bf9d3453395af72.0bf9d3453395af72<:0bf9d3453395af72|[channel=0xb030641f, /10.10.54.27:48451 -> /10.10.28.3:25662, epid=0x1e]
10.10.54.28
8760e1eae5d5aa4b.8760e1eae5d5aa4b<:8760e1eae5d5aa4b|[channel=0xe1440127, /10.10.54.28:42986 -> /10.10.49.9:25672, epid=0x1a]
2d676a4d381b30b4.2d676a4d381b30b4<:2d676a4d381b30b4|[channel=0xe1440127, /10.10.54.28:42986 -> /10.10.49.9:25672, epid=0x1a]
35fb8a6327f1cdb4.35fb8a6327f1cdb4<:35fb8a6327f1cdb4|Connecting to Redis at 10.10.49.9:25672
```
> 日志的分析可以把问题定位到10.10.28.3:25662，10.10.49.9:25672这两台节点  
**日志分析真的很重要，因为这里是第一现场**  
## lettuce源码排查 
1 lettuce 初始化 *Partitions.java*
```
    /**
        * Update the partition cache. Updates are necessary after the partition details have changed.
        */
    public void updateCache() {

        synchronized (partitions) {

            if (partitions.isEmpty()) {
                this.slotCache = EMPTY;
                this.nodeReadView = Collections.emptyList();
                return;
            }

            RedisClusterNode[] slotCache = new RedisClusterNode[SlotHash.SLOT_COUNT];
            List<RedisClusterNode> readView = new ArrayList<>(partitions.size());

            for (RedisClusterNode partition : partitions) {

                readView.add(partition);
                for (Integer integer : partition.getSlots()) {
                    slotCache[integer.intValue()] = partition;
                }
            }

            this.slotCache = slotCache;
            this.nodeReadView = Collections.unmodifiableCollection(readView);
        }
    }
```
2 lettuce 发送命令 *PooledClusterConnectionProvider.java*
```
    private CompletableFuture<StatefulRedisConnection<K, V>> getWriteConnection(int slot) {

        CompletableFuture<StatefulRedisConnection<K, V>> writer;// avoid races when reconfiguring partitions.
        synchronized (stateLock) {
            writer = writers[slot];
        }

        if (writer == null) {
            RedisClusterNode partition = partitions.getPartitionBySlot(slot);
            if (partition == null) {
                clusterEventListener.onUncoveredSlot(slot);
                return Futures.failed(new PartitionSelectorException("Cannot determine a partition for slot " + slot + ".",
                        partitions.clone()));
            }

            // Use always host and port for slot-oriented operations. We don't want to get reconnected on a different
            // host because the nodeId can be handled by a different host.
            RedisURI uri = partition.getUri();
            ConnectionKey key = new ConnectionKey(Intent.WRITE, uri.getHost(), uri.getPort());

            ConnectionFuture<StatefulRedisConnection<K, V>> future = getConnectionAsync(key);

            return future.thenApply(connection -> {

                synchronized (stateLock) {
                    if (writers[slot] == null) {
                        writers[slot] = CompletableFuture.completedFuture(connection);
                    }
                }

                return connection;
            }).toCompletableFuture();
        }

        return writer;
    }
```
> lettuce的发送原理：
>1. 在client启动的时候加载拓扑结构，本地以一个数组的结构**slotCache**将slot和node的映射关系存储下来  
>2. 发送的时候，计算完key的CRC16之后，通过slot去数组**slotCache**中获取到对应的node，继续拿到这个node的connection  
>3. **注意基本上所有这种集群模式的中间件，客户端的逻辑都是获取server端的网络拓扑，然后在client计算映射逻辑**，  
对比看kafka跨机房性能分析：https://blog.csdn.net/singgel/article/details/104281925  
中有对这个mapping映射的描述  
## redis cluster信息排查 
./bin/redis-cli -h 10.10.28.2 -p 25661 cluster info
```
cluster_state:ok
cluster_slots_assigned:16384
cluster_slots_ok:16384
cluster_slots_pfail:0
cluster_slots_fail:0
cluster_known_nodes:6
cluster_size:3
cluster_current_epoch:8
cluster_my_epoch:6
cluster_stats_messages_ping_sent:615483
cluster_stats_messages_pong_sent:610194
cluster_stats_messages_meet_sent:3
cluster_stats_messages_fail_sent:8
cluster_stats_messages_auth-req_sent:5
cluster_stats_messages_auth-ack_sent:2
cluster_stats_messages_update_sent:4
cluster_stats_messages_sent:1225699
cluster_stats_messages_ping_received:610188
cluster_stats_messages_pong_received:603593
cluster_stats_messages_meet_received:2
cluster_stats_messages_fail_received:4
cluster_stats_messages_auth-req_received:2
cluster_stats_messages_auth-ack_received:2
cluster_stats_messages_received:1213791
```
./bin/redis-cli -h 10.10.28.2 -p 25661 cluster nodes
```
5e9d0c185a2ba2fc9564495730c874bea76c15fa 10.10.28.3:25662@35662 slave 2281f330d771ee682221bc6c239afd68e6f20571 0 1595921769000 15 connected
79cb673db12199c32737b959cd82ec9963106558 10.10.25.2:25651@35651 master - 0 1595921770000 18 connected 4096-6143
2281f330d771ee682221bc6c239afd68e6f20571 10.10.28.2:25661@35661 myself,master - 0 1595921759000 15 connected 10240-12287
6a9ea568d6b49360afbb650c712bd7920403ba19 10.10.28.3:25686@35686 master - 0 1595921769000 14 connected 12288-14335
5a12dd423370e6f4085e593f9cd0b3a4ddfa9757 10.10.27.2:25656@35656 master - 0 1595921771000 13 connected 14336-16383
f5148dba1127bd9bada8ecc39341a0b72ef25d8e 10.10.25.3:25652@35652 slave 79cb673db12199c32737b959cd82ec9963106558 0 1595921769000 18 connected
f6788b4829e601642ed4139548153830c430b932 10.10.26.3:25666@35666 master - 0 1595921769870 16 connected 8192-10239
f54cfebc12c69725f471d16133e7ca3a8567dc18 10.10.28.15:25687@35687 slave 6a9ea568d6b49360afbb650c712bd7920403ba19 0 1595921763000 14 connected
f09ad21effff245cae23c024a8a886f883634f5c 10.10.28.15:25667@35667 slave f6788b4829e601642ed4139548153830c430b932 0 1595921770870 16 connected
ff5f5a56a7866f32e84ec89482aabd9ca1f05e20 10.10.25.3:25681@35681 master - 0 1595921773876 0 connected 0-2047
19c57214e4293b2e37d881534dcd55318fa96a70 10.10.50.16:25677@35677 slave 5f677e012808b09c67316f6ac5bdf0ec005cd598 0 1595921768000 17 connected
d8b4f99e0f9961f2e866b92e7351760faa3e0f2b 10.10.30.9:25671@35671 master - 0 1595921773000 6 connected 2048-4095
068e3bc73c27782c49782d30b66aa8b1140666ce 10.10.27.3:25682@35682 slave ff5f5a56a7866f32e84ec89482aabd9ca1f05e20 0 1595921771872 12 connected
e8b0311aeec4e3d285028abc377f0c277f9a5c74 10.10.49.9:25672@35672 slave d8b4f99e0f9961f2e866b92e7351760faa3e0f2b 0 1595921770000 6 connected
f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305 10.10.27.3:25657@35657 slave 5a12dd423370e6f4085e593f9cd0b3a4ddfa9757 0 1595921762000 13 connected
5f677e012808b09c67316f6ac5bdf0ec005cd598 10.10.50.7:25676@35676 master - 0 1595921772873 17 connected 6144-8191
```
./bin/redis-cli -h 10.10.28.3 -p 25662 cluster nodes
```
f5148dba1127bd9bada8ecc39341a0b72ef25d8e 10.10.25.3:25652@35652 slave 79cb673db12199c32737b959cd82ec9963106558 0 1595921741000 18 connected
f6788b4829e601642ed4139548153830c430b932 10.10.26.3:25666@35666 master - 0 1595921744000 16 connected 8192-10239
f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305 10.10.27.3:25657@35657 slave 5a12dd423370e6f4085e593f9cd0b3a4ddfa9757 0 1595921740000 13 connected
5f677e012808b09c67316f6ac5bdf0ec005cd598 10.10.50.7:25676@35676 master - 0 1595921743127 17 connected 6144-8191
79cb673db12199c32737b959cd82ec9963106558 10.10.25.2:25651@35651 master - 0 1595921743000 18 connected 4096-6143
2281f330d771ee682221bc6c239afd68e6f20571 10.10.28.2:25661@35661 master - 0 1595921744129 15 connected 10240-12287
f09ad21effff245cae23c024a8a886f883634f5c 10.10.28.15:25667@35667 slave f6788b4829e601642ed4139548153830c430b932 0 1595921740000 16 connected
f54cfebc12c69725f471d16133e7ca3a8567dc18 10.10.28.15:25687@35687 slave 6a9ea568d6b49360afbb650c712bd7920403ba19 0 1595921745130 14 connected
5e9d0c185a2ba2fc9564495730c874bea76c15fa 10.10.28.3:25662@35662 myself,slave 2281f330d771ee682221bc6c239afd68e6f20571 0 1595921733000 5 connected 0-1820
068e3bc73c27782c49782d30b66aa8b1140666ce 10.10.27.3:25682@35682 slave ff5f5a56a7866f32e84ec89482aabd9ca1f05e20 0 1595921744000 12 connected
d8b4f99e0f9961f2e866b92e7351760faa3e0f2b 10.10.30.9:25671@35671 master - 0 1595921739000 6 connected 2048-4095
5a12dd423370e6f4085e593f9cd0b3a4ddfa9757 10.10.27.2:25656@35656 master - 0 1595921742000 13 connected 14336-16383
ff5f5a56a7866f32e84ec89482aabd9ca1f05e20 10.10.25.3:25681@35681 master - 0 1595921746131 0 connected 1821-2047
6a9ea568d6b49360afbb650c712bd7920403ba19 10.10.28.3:25686@35686 master - 0 1595921747133 14 connected 12288-14335
19c57214e4293b2e37d881534dcd55318fa96a70 10.10.50.16:25677@35677 slave 5f677e012808b09c67316f6ac5bdf0ec005cd598 0 1595921742126 17 connected
e8b0311aeec4e3d285028abc377f0c277f9a5c74 10.10.49.9:25672@35672 slave d8b4f99e0f9961f2e866b92e7351760faa3e0f2b 0 1595921745000 6 connected
```
./bin/redis-cli -h 10.10.49.9 -p 25672 cluster nodes
```
d8b4f99e0f9961f2e866b92e7351760faa3e0f2b 10.10.30.9:25671@35671 master - 0 1595921829000 6 connected 2048-4095
79cb673db12199c32737b959cd82ec9963106558 10.10.25.2:25651@35651 master - 0 1595921830000 18 connected 4096-6143
ff5f5a56a7866f32e84ec89482aabd9ca1f05e20 10.10.25.3:25681@35681 master - 0 1595921830719 0 connected 0-1820
f54cfebc12c69725f471d16133e7ca3a8567dc18 10.10.28.15:25687@35687 slave 6a9ea568d6b49360afbb650c712bd7920403ba19 0 1595921827000 14 connected
5f677e012808b09c67316f6ac5bdf0ec005cd598 10.10.50.7:25676@35676 master - 0 1595921827000 17 connected 6144-8191
2281f330d771ee682221bc6c239afd68e6f20571 10.10.28.2:25661@35661 master - 0 1595921822000 15 connected 10240-12287
5e9d0c185a2ba2fc9564495730c874bea76c15fa 10.10.28.3:25662@35662 slave 2281f330d771ee682221bc6c239afd68e6f20571 0 1595921828714 15 connected
068e3bc73c27782c49782d30b66aa8b1140666ce 10.10.27.3:25682@35682 slave ff5f5a56a7866f32e84ec89482aabd9ca1f05e20 0 1595921832721 12 connected
6a9ea568d6b49360afbb650c712bd7920403ba19 10.10.28.3:25686@35686 master - 0 1595921825000 14 connected 12288-14335
f5148dba1127bd9bada8ecc39341a0b72ef25d8e 10.10.25.3:25652@35652 slave 79cb673db12199c32737b959cd82ec9963106558 0 1595921830000 18 connected
19c57214e4293b2e37d881534dcd55318fa96a70 10.10.50.16:25677@35677 slave 5f677e012808b09c67316f6ac5bdf0ec005cd598 0 1595921829716 17 connected
e8b0311aeec4e3d285028abc377f0c277f9a5c74 10.10.49.9:25672@35672 myself,slave d8b4f99e0f9961f2e866b92e7351760faa3e0f2b 0 1595921832000 4 connected 1821-2047
f09ad21effff245cae23c024a8a886f883634f5c 10.10.28.15:25667@35667 slave f6788b4829e601642ed4139548153830c430b932 0 1595921826711 16 connected
f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305 10.10.27.3:25657@35657 slave 5a12dd423370e6f4085e593f9cd0b3a4ddfa9757 0 1595921829000 13 connected
f6788b4829e601642ed4139548153830c430b932 10.10.26.3:25666@35666 master - 0 1595921831720 16 connected 8192-10239
5a12dd423370e6f4085e593f9cd0b3a4ddfa9757 10.10.27.2:25656@35656 master - 0 1595921827714 13 connected 14336-16383
```
./bin/redis-trib.rb check 10.10.30.9:25671
```
>>> Performing Cluster Check (using node 10.10.30.9:25671)
M: d8b4f99e0f9961f2e866b92e7351760faa3e0f2b 10.10.30.9:25671
   slots:2048-4095 (2048 slots) master
   1 additional replica(s)
S: e8b0311aeec4e3d285028abc377f0c277f9a5c74 10.10.49.9:25672
   slots: (0 slots) slave
   ········
   ········
S: f03bc2ca91b3012f4612ecbc8c611c9f4a0e1305 10.10.27.3:25657
   slots: (0 slots) slave
   replicates 5a12dd423370e6f4085e593f9cd0b3a4ddfa9757
M: 5a12dd423370e6f4085e593f9cd0b3a4ddfa9757 10.10.27.2:25656
   slots:14336-16383 (2048 slots) master
   1 additional replica(s)
[ERR] Nodes don't agree about configuration!
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
```
>**对一切保持怀疑，提高警惕，勤能补拙**  
>1.最开始就怀疑了集群是不是健康的，但是由于线上的现象表示出：大部分节点正常，几个别出现问题，重启问题fix  
>2.一开始是通过正常的节点进去查看的信息，导致没有发现问题，就算一开始通过日志去查看了几个node的拓扑信息不一致，要是没有客户端的映射关系，还是很难看出问题  
>3.对比发现部分slot映射到了slave节点  
>4.执行了check之后，发现集群是有问题的，后面的开源相关的issue里面都有解释  
# 问题复盘  

# 优化改进  
## redis cluster监控
在监控系统中加入常规扫描：在redis5的版本中client加入了cluster的一些治理命令  
> redis-cli --cluster check 127.0.0.1:6379  
## redis manager工具
在集群的任何变更之后都进行：  
> redis-trib.rb check 127.0.0.1:6379  

# 开源相关
## lettuce
gitter chat: https://gitter.im/lettuce-io/Lobby  
readonly refresh topology issue: https://github.com/lettuce-io/lettuce-core/issues/1365  
## redis
gossip: https://github.com/redis/redis/issues/2055  
slots mapping: https://github.com/redis/redis/issues/2969  