package com.itqiuan.shortlink;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.itqiuan.shortlink.mapper")
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Short Link API", version = "1.0.0", description = "API Documentation for Short Link Service"))
public class ShortLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkApplication.class, args);
    }

}
