package com.example.face2info.client;

import com.example.face2info.entity.internal.VisionModelSearchResult;

import java.util.List;

public interface VisionPersonSearchClient {

    List<VisionModelSearchResult> searchPersonByImageUrl(String imageUrl);
}
