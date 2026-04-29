package com.example.face2info.client;

import com.example.face2info.entity.response.SocialAccount;

import java.util.List;

/**
 * 基于用户名调用 Maigret 查找疑似社交账号。
 */
public interface MaigretClient {

    List<SocialAccount> findSuspectedAccounts(String username);
}
