package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author admin
 * @version 1.0.0
 * @ClassName RedisConfig.java
 * @Description TODO
 * @createTime 2023年02月28日 13:10:00
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 Redisson客户端
     * @return
     */
    @Bean
    public RedissonClient redissonClient(){
        // 配置类
        Config config = new Config();
        // 添加Redis地址，这里添加了单点的地址，也可以使用 config.useClusterServers() 添加集群地址
        config.useSingleServer().setAddress("redis://localhost:6379").setDatabase(0);
        // 创建客户端
        return  Redisson.create(config);
    }
}
