package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 用户名 OSINT 工具反查配置。
 */
@Getter
@Setter
public class MaigretProperties {

    private boolean enabled;
    private String projectPath = "D:/ideaProject/maigret";
    private String executable = "D:/ideaProject/maigret/.venv/Scripts/maigret.exe";
    private List<String> commandPrefix = new ArrayList<>();
    private int topSites = 50;
    private int siteTimeoutSeconds = 10;
    private int processTimeoutMs = 60000;
    private int maxUsernames = 5;
    private int maxAccountsPerUsername = 30;
    private boolean noRecursion = true;
    private boolean noAutoupdate = true;
    private Tool sherlock = new Tool(
            true,
            "D:/ideaProject/sherlock",
            "D:/ideaProject/sherlock/.venv/Scripts/sherlock.exe",
            "sherlock"
    );
    private Tool tookie = new Tool(
            true,
            "D:/ideaProject/tookie-osint",
            "D:/ideaProject/tookie-osint/.venv/Scripts/python.exe",
            "brib.py"
    );
    private List<String> socialSites = new ArrayList<>(Arrays.asList(
            "Facebook",
            "YouTube",
            "Instagram",
            "Twitter",
            "X",
            "TikTok",
            "LinkedIn",
            "GitHub",
            "Reddit",
            "Pinterest",
            "Tumblr",
            "Twitch",
            "Flickr",
            "Vimeo",
            "SoundCloud",
            "Spotify",
            "Medium",
            "Quora",
            "VK",
            "Telegram",
            "Snapchat",
            "Mastodon",
            "Threads",
            "Bluesky",
            "Discord",
            "Steam",
            "StackOverflow",
            "GitLab",
            "BitBucket",
            "Behance",
            "Dribbble",
            "DeviantArt",
            "ArtStation",
            "Goodreads",
            "Kaggle",
            "HackerNews",
            "ProductHunt",
            "Keybase",
            "Patreon",
            "BuyMeACoffee",
            "Linktree",
            "AskFM",
            "Weibo",
            "Zhihu",
            "Douban",
            "Bilibili",
            "Gitee",
            "Myspace",
            "About.me",
            "Last.fm"
    ));

    @Getter
    @Setter
    public static class Tool {

        private boolean enabled;
        private String projectPath;
        private String executable;
        private String scriptPath;
        private List<String> commandPrefix = new ArrayList<>();

        public Tool() {
        }

        public Tool(boolean enabled, String projectPath, String executable, String scriptPath) {
            this.enabled = enabled;
            this.projectPath = projectPath;
            this.executable = executable;
            this.scriptPath = scriptPath;
        }
    }
}
