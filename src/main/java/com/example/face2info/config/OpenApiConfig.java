package com.example.face2info.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 文档配置。
 */
public class OpenApiConfig {

    @Bean
    public OpenAPI face2InfoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Face2Info API")
                .description("上传人脸照片后聚合人物实体信息、社交账号和新闻")
                .version("1.0.0"));
    }
}
