package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    //这里的泛型对应于脚本中的返回值类型
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    //尝试获取锁
    public Boolean tryLock(long timeoutSec) {
        //获取uuid + 线程id相结合的标识
        String threadId = Thread.currentThread().getId() + ID_PREFIX;
        //1.获取锁、设置过期时间
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //2.返回获取锁的结果
        return Boolean.TRUE.equals(result);
    }

    //释放锁
    public void unlock() {
        //判断存入redis中的线程标识是否发生了改变
        stringRedisTemplate.execute(UNLOCK_SCRIPT
                , Collections.singletonList(KEY_PREFIX + name)
                , ID_PREFIX + Thread.currentThread().getId());
    }

}
