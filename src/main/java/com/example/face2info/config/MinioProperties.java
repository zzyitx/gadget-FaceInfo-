package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MinioProperties {

    private String endpoint = "http://192.168.216.133:9000";
    private String publicEndpoint = "http://192.168.216.133:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "face-bucket";
}
