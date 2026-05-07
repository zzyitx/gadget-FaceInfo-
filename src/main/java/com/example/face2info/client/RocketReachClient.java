package com.example.face2info.client;

import com.example.face2info.entity.response.SocialAccount;

import java.util.List;

/**
 * RocketReach 人物资料搜索客户端抽象。
 */
public interface RocketReachClient {

    /**
     * 按人物姓名搜索公开职业资料和社交主页。
     */
    List<SocialAccount> findProfileAccounts(String name);
}
