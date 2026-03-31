package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

public class FaceCheckUploadResponse {

    private String idSearch;
    private List<FaceCheckMatchCandidate> items = new ArrayList<>();

    public String getIdSearch() {
        return idSearch;
    }

    public FaceCheckUploadResponse setIdSearch(String idSearch) {
        this.idSearch = idSearch;
        return this;
    }

    public List<FaceCheckMatchCandidate> getItems() {
        return items;
    }

    public FaceCheckUploadResponse setItems(List<FaceCheckMatchCandidate> items) {
        this.items = items;
        return this;
    }
}
