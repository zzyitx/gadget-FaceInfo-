package com.example.face2info.entity.internal;

public class FaceCheckUploadResponse {

    private String idSearch;
    private String message;

    public String getIdSearch() {
        return idSearch;
    }

    public FaceCheckUploadResponse setIdSearch(String idSearch) {
        this.idSearch = idSearch;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public FaceCheckUploadResponse setMessage(String message) {
        this.message = message;
        return this;
    }
}
