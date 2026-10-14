package com.itqiuan.shortlink;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类。
 * <p>
 * {@code @MapperScan} 指定 Mapper 接口所在包，否则 MyBatis-Plus 的 Mapper 不会被注册成 Bean。
 * {@code @OpenAPIDefinition} 提供全局 API 文档元信息 —— 这组信息写在 yml 里是无效的
 * （springdoc 根本没有 api-docs.info.* 属性，会静默失效），只能走注解。
 */
@MapperScan("com.itqiuan.shortlink.mapper")
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Short Link API", version = "1.0.0", description = "API Documentation for Short Link Service"))
public class ShortLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkApplication.class, args);
    }

}
