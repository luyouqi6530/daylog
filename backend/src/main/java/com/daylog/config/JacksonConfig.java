package com.daylog.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置
 *
 * <p>雪花ID 是 19 位 Long，而 JS 的 Number 只有 53 位安全整数，
 * 直接以数字返回前端会精度丢失（末几位变 0），导致按 ID 查询/
 * 更新永远对不上。这里全局把 Long 序列化为字符串。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
