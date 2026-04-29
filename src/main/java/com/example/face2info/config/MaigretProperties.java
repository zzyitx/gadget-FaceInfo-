package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Maigret 用户名反查配置。
 */
@Getter
@Setter
public class MaigretProperties {

    private boolean enabled;
    private String executable = "maigret";
    private List<String> commandPrefix = new ArrayList<>();
    private int topSites = 200;
    private int siteTimeoutSeconds = 10;
    private int processTimeoutMs = 60000;
    private int maxUsernames = 3;
    private int maxAccountsPerUsername = 30;
    private boolean noRecursion = true;
    private boolean noAutoupdate = true;
}
