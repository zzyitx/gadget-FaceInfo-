package com.example.face2info.client.impl;

import com.example.face2info.client.OsintSocialAccountClient;
import com.example.face2info.entity.response.SocialAccount;

import java.util.List;

public class NoopOsintSocialAccountClient implements OsintSocialAccountClient {

    @Override
    public List<SocialAccount> findSuspectedAccounts(String username) {
        return List.of();
    }
}
