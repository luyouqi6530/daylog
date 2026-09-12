package com.daylog.common.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Result 统一响应单元测试
 *
 * <p>前端依赖的统一响应结构必须保证：code/message/data 三字段语义正确，
 * success()/fail() 工厂方法生成的实例符合预期。</p>
 */
@DisplayName("Result 统一响应单元测试")
class ResultTest {

    @Test
    @DisplayName("success()：code=200，message=操作成功，data=null")
    void success_noData() {
        Result<String> r = Result.success();

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("操作成功");
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("success(data)：data 字段正确传透")
    void success_withData() {
        List<Integer> data = List.of(1, 2, 3);
        Result<List<Integer>> rr = Result.success(data);

        assertThat(rr.getCode()).isEqualTo(200);
        assertThat(rr.getData()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("fail(ResultCode)：使用 ResultCode 的 code 与默认 message")
    void fail_withResultCode() {
        Result<String> r = Result.fail(ResultCode.USERNAME_OR_PASSWORD_ERROR);

        assertThat(r.getCode()).isEqualTo(1004);
        assertThat(r.getMessage()).isEqualTo("用户名或密码错误");
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("fail(int, String)：自定义 code + 消息")
    void fail_withCodeAndMessage() {
        Result<String> r = Result.fail(9999, "自定义错误");

        assertThat(r.getCode()).isEqualTo(9999);
        assertThat(r.getMessage()).isEqualTo("自定义错误");
    }

    @Test
    @DisplayName("fail(ResultCode, message)：code 用枚举、message 可自定义覆盖默认")
    void fail_withResultCodeAndCustomMessage() {
        Result<String> r = Result.fail(ResultCode.NOT_FOUND, "日记不存在");

        assertThat(r.getCode()).isEqualTo(404);
        assertThat(r.getMessage()).isEqualTo("日记不存在");
    }

    @Test
    @DisplayName("Result 不可被业务代码 new：构造器私有，只能走工厂方法")
    void constructorIsPrivate() throws Exception {
        // 验证 private 构造器（防止有人绕过工厂方法 new Result()）
        Constructor<?> ctor = Result.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("ResultCode 关键枚举值稳定：业务错码 1001/1002/1003/1004/3004/3005 不可漂移")
    void resultCode_constantsStable() {
        // 防止有人误改枚举值导致前端联动失效
        assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ResultCode.UNAUTHORIZED.getCode()).isEqualTo(401);
        assertThat(ResultCode.NOT_FOUND.getCode()).isEqualTo(404);
        assertThat(ResultCode.USERNAME_EXISTS.getCode()).isEqualTo(1001);
        assertThat(ResultCode.DIARY_ALREADY_EXISTS.getCode()).isEqualTo(1002);
        assertThat(ResultCode.TAG_NAME_EXISTS.getCode()).isEqualTo(1003);
        assertThat(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode()).isEqualTo(1004);
        assertThat(ResultCode.AI_GENERATE_FAILED.getCode()).isEqualTo(3004);
        assertThat(ResultCode.AI_NO_DIARY_THIS_WEEK.getCode()).isEqualTo(3005);
    }
}