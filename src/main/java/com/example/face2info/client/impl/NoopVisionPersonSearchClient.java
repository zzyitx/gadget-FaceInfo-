package com.example.face2info.client.impl;

import com.example.face2info.client.VisionPersonSearchClient;
import com.example.face2info.entity.internal.VisionModelSearchResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "face2info.api.sophnet-vision", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopVisionPersonSearchClient implements VisionPersonSearchClient {

    @Override
    public List<VisionModelSearchResult> searchPersonByImageUrl(String imageUrl) {
        return List.of();
    }
}
