package com.daylog;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文加载测试。
 *
 * <p>默认禁用——{@code @SpringBootTest} 会加载完整应用上下文，
 * 需要 MySQL/Redis 可达且 DeepSeek 配置完整；纯单元测试场景下无意义。
 * 如需做集成测试请配合 Testcontainers / H2 内存数据库独立启动。</p>
 */
@SpringBootTest
@Disabled("需要 MySQL/Redis/DeepSeek 全链路可达；纯单元测试套件中跳过")
class DaylogApplicationTests {

    @Test
    void contextLoads() {
    }
}
