package com.example.face2info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Face2Info 应用启动入口。
 * 仅扫描新实现所在包，避免旧实现残留 Bean 与控制器继续生效。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.example.face2info.config")
public class Face2infoApplication {

    public static void main(String[] args) {
        SpringApplication.run(Face2infoApplication.class, args);
    }
}
