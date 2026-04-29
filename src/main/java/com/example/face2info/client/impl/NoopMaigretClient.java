package com.example.face2info.client.impl;

import com.example.face2info.client.MaigretClient;
import com.example.face2info.entity.response.SocialAccount;

import java.util.List;

public class NoopMaigretClient implements MaigretClient {

    @Override
    public List<SocialAccount> findSuspectedAccounts(String username) {
        return List.of();
    }
}
