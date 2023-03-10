package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author wzy
 * @title 基于 Redis的全局唯一ID生成器 工具类
 * @description 全局唯一ID(共64位)信息组成：
 *      1.符号位：0
 *      2.时间戳：31位 (当前时间 - 初始时间)
 *      3.序列号：32位
 * @updateTime 2023/2/24 13:49
 * @throws
 */
@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1672531200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回：时间戳向左移动32位   剩下的32位：或运算  自增长数据填充
        return timestamp << COUNT_BITS | count;
    }

    /**
     * 可用于生成某年的起始时间
     * @param args
     */
    public static void main(String[] args) {
        // 指定获取2023年的起始时间
        LocalDateTime time = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
        // 获取秒数(时区)
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second=" + second);
    }
}
