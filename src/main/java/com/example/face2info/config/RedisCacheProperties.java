package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RedisCacheProperties {

    private boolean enabled = false;
    private String keyPrefix = "face2info";
    private long recognitionTtlSeconds = 86400;
    private long finalResponseTtlSeconds = 21600;
}
