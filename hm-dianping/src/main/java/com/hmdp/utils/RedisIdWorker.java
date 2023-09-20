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
 *      2.时间戳：31位 (当前时间戳 - 初始时间戳)
 *      3.序列号：32位
 * 数据库存储的 id 为 long类型, 8个字节即 64位
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
        // 2.2.自增长(占位: 32位)
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回：时间戳向左移动32位   剩下的32位：或运算  自增长数据填充
        // 数据转换成二进制数后，向左移若干位，高位丢弃，低位补零
        return timestamp << COUNT_BITS | count;
    }

    /**
     * 可用于生成某年的起始时间
     * @param args
     */
    public static void main(String[] args) {
        // 指定获取2023年的起始时间
        LocalDateTime time = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
        // 获取秒数(时区偏移量) 1970-01-01T00:00:00Z
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second=" + second);

//        int i = 10 << 5;
//        System.out.println(i);
    }
}
