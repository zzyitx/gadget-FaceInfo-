package com.example.face2info.client.impl;

import com.example.face2info.client.RocketReachClient;
import com.example.face2info.entity.response.SocialAccount;

import java.util.List;

public class NoopRocketReachClient implements RocketReachClient {

    @Override
    public List<SocialAccount> findProfileAccounts(String name) {
        return List.of();
    }
}
