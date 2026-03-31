package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

public class FaceCheckSearchResponse {

    private List<FaceCheckMatchCandidate> items = new ArrayList<>();
    private boolean timedOut;

    public List<FaceCheckMatchCandidate> getItems() {
        return items;
    }

    public FaceCheckSearchResponse setItems(List<FaceCheckMatchCandidate> items) {
        this.items = items;
        return this;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public FaceCheckSearchResponse setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
        return this;
    }
}
