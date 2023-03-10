package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @title 自定义 Redis分布式锁 工具类
 * @description
 * @author wzy
 * @updateTime 2023/2/27 20:28
 * @throws
 */
public class SimpleRedisLock implements ILock {

    private String name; // 业务名
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock() {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:"; // 锁前缀
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static { // 静态代码块，在类加载时就会初始化 lua脚本
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")); // 设置脚本位置（ClassPath下的资源）
        UNLOCK_SCRIPT.setResultType(Long.class); // 设置返回值类型
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success); // 避免空指针，防止包装类拆箱出错
    }


    /**
     * Redis-cli中 Lua脚本使用实例：
     * 0个参数：eval "return redis.call('set','name','jack')" 0
     * 1个参数：eval "return redis.call('set',KEYS[1],ARGV[1])" 1 name rose
     * 2个参数：eval "return redis.call('mset',KEYS[1],ARGV[1],KEYS[2],ARGV[2])" 2 username password wawa 123
     *
     */
    @Override
    public void unlock() {
        // RedisTemplate调用lua脚本的API： execute(脚本, keys[],argv[])
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
    /*@Override
    public void unlock() {
        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁中的标示
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 判断标示是否一致
        if(threadId.equals(id)) {
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
