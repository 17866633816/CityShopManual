package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;


/**
 * redis工具类
 */
@Component
public class RedisClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R,ID> R queryByIdWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,
                                             Long time, TimeUnit unit){

        //从redis中查询此id对应的商店信息
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //判断redis中是否存在此缓存
        if (StrUtil.isNotBlank(json)){
            //有此信息，返回给客户端
            R r = JSONUtil.toBean(json, type);
            return r;
        }

        //查看是否是空值。这里的cache不是 null 就是 ""
        if (json != null){
            //是""，说明是缓存穿透问题
            return null;
        }

        //无此信息，从数据库中查询。此处涉及到函数式编程
        R r = dbFallBack.apply(id);

        //判断数据库中是否存在
        if (r == null){
            //不存在
            //向redis中存入一个空字符串，并设置存活时间
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            // 返回错误信息
            return null;
        }

        //存在，保存到redis中一份，并设置过期时间
        this.set(key, r, time, unit);

        //发给客户端一份
        return r;
    }


    //逻辑过期解决缓存击穿
    //Function<ID,R> 表示dbFallBack函数的输入参数为 ID 类型，表示要查询的对象的标识符，输出参数为 R 类型，表示从数据库中获取到的数据类型。
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R,ID> R queryByIdWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,
                                               Long time, TimeUnit unit){

        //1.从redis查询店铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断redis中是否存在
        if (StrUtil.isBlank(json)){
            //3.不存在，返回null
            return null;
        }

        //4.命中，需要将JSON反序列化为RedisData对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.根据逻辑删除字段判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期，直接返回店铺信息
            return r;
        }
        //5.2 已过期，需要缓存重建
        //6.缓存重建
        //6.1 获取互斥锁
        String lockKey = "LOCK_KEY" + id;
        boolean isLock = tryLock(lockKey);
        //6.2 判断是否获取锁成功
        if (isLock){
            //6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R value = dbFallBack.apply(id);
                    //存入redis
                    this.setWithLogicalExpire(key, value, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.4 返回过期的店铺信息
        return r;

    }


    //获取锁
    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL , TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag); //flag为true时返回true，为false和null时返回false
    }

    //释放锁
    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
