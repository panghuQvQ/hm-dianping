# hm-dianping
Redis 实战项目---黑马点评项目

## 运行流程
- 创建数据库，创建表，运行 hmdp.sql 文件
- 运行 `ShopControllerTest.java`,把商铺根据类型分组，并将其经纬度存入 Redis 中
- 再redis-cli 中创建消费者组,执行语句如下
  - xgroup create stream.orders consumerGroup 0 MKSTREAM
- 打开 nginx
- 使用 Copy Configuration 创建另一个服务，并设置启动参数
  - -Dserver.port=8082 -DConsumerGroup.consumer=qwe

JMeter 压测工具----`秒杀抢购多人.jmx` 压测文件
