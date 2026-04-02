package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "FaceCheck 搜索接口响应")
public class FaceCheckSearchResponse {

    @Schema(description = "FaceCheck 返回的候选结果列表")
    private List<FaceCheckMatchCandidate> items = new ArrayList<>();

    @Schema(description = "是否出现超时")
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
