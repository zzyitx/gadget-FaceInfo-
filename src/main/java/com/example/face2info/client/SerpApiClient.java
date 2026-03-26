package com.example.face2info.client;

import com.example.face2info.entity.internal.SerpApiResponse;

public interface SerpApiClient {

    SerpApiResponse reverseImageSearchByUrl(String imageUrl);

    SerpApiResponse googleSearch(String query);
}
