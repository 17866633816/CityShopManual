package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        //创建配置类
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.198.135:6379").setPassword("123456");
        //创建RedissonClient对象
        return Redisson.create(config);
    }

}
