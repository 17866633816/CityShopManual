package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 利用 Redis 生成全局唯一ID
 */
@Component
public class RedisIdWorker {

    //下面这个时间戳是在ZoneOffset.UTC时区下2022年1月1号0点0分0秒对应的秒数
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    //序列号的位数
    private static final long COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long currentSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = currentSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3.拼接并返回
        return timeStamp << COUNT_BITS | count;
    }


    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long seconds = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(seconds);
    }

}
