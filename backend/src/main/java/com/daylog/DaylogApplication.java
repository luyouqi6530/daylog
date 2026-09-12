package com.daylog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Daylog 启动类
 *
 * <p>说明：不使用包级 {@code @MapperScan("com.daylog.modules")}——那样会把 service 接口也误注册为
 * MyBatis Mapper 代理（service 与 mapper 同处 modules 包内）。改为各 Mapper 接口显式标注
 * {@code @Mapper}，由 MyBatis-Spring 自动扫描注册，范围精确、无歧义。</p>
 */
@EnableScheduling
@SpringBootApplication
public class DaylogApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaylogApplication.class, args);
    }
}
