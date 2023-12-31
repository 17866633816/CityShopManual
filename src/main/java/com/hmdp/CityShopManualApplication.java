package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;



@EnableAspectJAutoProxy(exposeProxy = true)
//扫描mapper包
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class CityShopManualApplication {

    public static void main(String[] args) {
        SpringApplication.run(CityShopManualApplication.class, args);
    }

}
