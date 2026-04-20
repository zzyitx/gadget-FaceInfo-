package com.example.face2info.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 文档配置。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI face2InfoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Face2Info API")
                .description("上传人脸照片后聚合人物公开资料、社交账号与图片匹配文章来源")
                .version("1.0.0"));
    }
}
