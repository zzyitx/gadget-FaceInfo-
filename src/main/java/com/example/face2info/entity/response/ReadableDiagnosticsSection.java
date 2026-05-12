package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "便于定位问题层级的诊断分区")
public class ReadableDiagnosticsSection {

    @Schema(description = "非阻塞告警")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "失败或部分失败原因")
    private String error;

    @Schema(description = "多脸选择信息")
    private FaceSelectionPayload selection;

    @JsonProperty("timing")
    @Schema(description = "流程耗时信息")
    private ReadableTiming timing = new ReadableTiming();

    @JsonProperty("section_status")
    @Schema(description = "按响应分区标记是否有内容，便于快速定位缺失层级")
    private Map<String, String> sectionStatus = new LinkedHashMap<>();

    public List<String> getWarnings() {
        return warnings;
    }

    public ReadableDiagnosticsSection setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        return this;
    }

    public String getError() {
        return error;
    }

    public ReadableDiagnosticsSection setError(String error) {
        this.error = error;
        return this;
    }

    public FaceSelectionPayload getSelection() {
        return selection;
    }

    public ReadableDiagnosticsSection setSelection(FaceSelectionPayload selection) {
        this.selection = selection;
        return this;
    }

    public ReadableTiming getTiming() {
        return timing;
    }

    public ReadableDiagnosticsSection setTiming(ReadableTiming timing) {
        this.timing = timing == null ? new ReadableTiming() : timing;
        return this;
    }

    public Map<String, String> getSectionStatus() {
        return sectionStatus;
    }

    public ReadableDiagnosticsSection setSectionStatus(Map<String, String> sectionStatus) {
        this.sectionStatus = sectionStatus == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sectionStatus);
        return this;
    }
}
