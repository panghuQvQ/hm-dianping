---
---/**
--     * Redis-cli中 Lua脚本使用实例：
--     * 0个参数：eval "return redis.call('set','name','jack')" 0
--     * 1个参数：eval "return redis.call('set',KEYS[1],ARGV[1])" 1 name rose
--     * 2个参数：eval "return redis.call('mset',KEYS[1],ARGV[1],KEYS[2],ARGV[2])" 2 username password wawa 123
--     *
--     */
---
---




-- 这里的 KEYS[1] 就是锁的key，这里的ARGV[1] 就是当前线程标示
-- 获取锁中的标示，判断是否与当前线程标示一致
if (redis.call('GET', KEYS[1]) == ARGV[1]) then
    -- 一致，则删除锁
    return redis.call('DEL', KEYS[1])
end
-- 不一致，则直接返回
return 0
