package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "人脸信息聚合响应")
public class FaceInfoResponse {

    @JsonProperty("schema_version")
    @Schema(description = "响应结构版本，2.0 表示按结论、候选、证据、诊断分区")
    private String schemaVersion = "2.0";

    @Schema(description = "最终结论分区")
    private ReadableResultSection result;

    @Schema(description = "候选信息分区")
    private ReadableCandidateSection candidates;

    @Schema(description = "证据分区")
    private ReadableEvidenceSection evidence;

    @Schema(description = "诊断分区")
    private ReadableDiagnosticsSection diagnostics;

    @JsonIgnore
    private PersonInfo person;

    @JsonIgnore
    private List<ImageMatch> imageMatches = new ArrayList<>();

    @JsonIgnore
    private List<ImageMatch> articleImageMatches = new ArrayList<>();

    @JsonIgnore
    private List<VisionModelPortrait> visionModelPortraits = new ArrayList<>();

    @JsonIgnore
    private List<CandidatePersonPortrait> candidatePersonPortraits = new ArrayList<>();

    @JsonIgnore
    private List<PersonPortraitGroup> personPortraitGroups = new ArrayList<>();

    @JsonIgnore
    private StructuredPortraits structuredPortraits;

    @JsonIgnore
    private List<String> warnings = new ArrayList<>();

    @JsonIgnore
    private FaceSelectionPayload selection;

    @Schema(description = "接口处理状态，常见值为 success、partial、failed、selection_required")
    private String status;

    @Schema(description = "请求失败或部分失败时的错误说明")
    private String error;

    @JsonIgnore
    private String startedAt;

    @JsonIgnore
    private String finishedAt;

    @JsonIgnore
    private Long durationMs;

    @JsonIgnore
    private String durationText;

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public FaceInfoResponse setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
        return this;
    }

    public ReadableResultSection getResult() {
        if (result == null) {
            result = buildResultSection();
        }
        return result;
    }

    public FaceInfoResponse setResult(ReadableResultSection result) {
        this.result = result;
        return this;
    }

    public ReadableCandidateSection getCandidates() {
        if (candidates == null) {
            candidates = buildCandidateSection();
        }
        return candidates;
    }

    public FaceInfoResponse setCandidates(ReadableCandidateSection candidates) {
        this.candidates = candidates;
        return this;
    }

    public ReadableEvidenceSection getEvidence() {
        if (evidence == null) {
            evidence = buildEvidenceSection();
        }
        return evidence;
    }

    public FaceInfoResponse setEvidence(ReadableEvidenceSection evidence) {
        this.evidence = evidence;
        return this;
    }

    public ReadableDiagnosticsSection getDiagnostics() {
        if (diagnostics == null) {
            diagnostics = buildDiagnosticsSection();
        }
        return diagnostics;
    }

    public FaceInfoResponse setDiagnostics(ReadableDiagnosticsSection diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

    public PersonInfo getPerson() {
        return person;
    }

    public FaceInfoResponse setPerson(PersonInfo person) {
        this.person = person;
        clearReadableSections();
        return this;
    }

    public List<ImageMatch> getImageMatches() {
        return imageMatches;
    }

    public FaceInfoResponse setImageMatches(List<ImageMatch> imageMatches) {
        this.imageMatches = imageMatches == null ? new ArrayList<>() : imageMatches;
        clearReadableSections();
        return this;
    }

    public List<ImageMatch> getArticleImageMatches() {
        return articleImageMatches;
    }

    public FaceInfoResponse setArticleImageMatches(List<ImageMatch> articleImageMatches) {
        this.articleImageMatches = articleImageMatches == null ? new ArrayList<>() : articleImageMatches;
        clearReadableSections();
        return this;
    }

    public List<VisionModelPortrait> getVisionModelPortraits() {
        return visionModelPortraits;
    }

    public FaceInfoResponse setVisionModelPortraits(List<VisionModelPortrait> visionModelPortraits) {
        this.visionModelPortraits = visionModelPortraits == null ? new ArrayList<>() : visionModelPortraits;
        clearReadableSections();
        return this;
    }

    public List<CandidatePersonPortrait> getCandidatePersonPortraits() {
        return candidatePersonPortraits;
    }

    public FaceInfoResponse setCandidatePersonPortraits(List<CandidatePersonPortrait> candidatePersonPortraits) {
        this.candidatePersonPortraits = candidatePersonPortraits == null ? new ArrayList<>() : candidatePersonPortraits;
        clearReadableSections();
        return this;
    }

    public List<PersonPortraitGroup> getPersonPortraitGroups() {
        return personPortraitGroups;
    }

    public FaceInfoResponse setPersonPortraitGroups(List<PersonPortraitGroup> personPortraitGroups) {
        this.personPortraitGroups = personPortraitGroups == null ? new ArrayList<>() : personPortraitGroups;
        clearReadableSections();
        return this;
    }

    public StructuredPortraits getStructuredPortraits() {
        return structuredPortraits;
    }

    public FaceInfoResponse setStructuredPortraits(StructuredPortraits structuredPortraits) {
        this.structuredPortraits = structuredPortraits;
        clearReadableSections();
        return this;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public FaceInfoResponse setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
        clearReadableSections();
        return this;
    }

    public FaceSelectionPayload getSelection() {
        return selection;
    }

    public FaceInfoResponse setSelection(FaceSelectionPayload selection) {
        this.selection = selection;
        clearReadableSections();
        return this;
    }

    public String getStatus() {
        return status;
    }

    public FaceInfoResponse setStatus(String status) {
        this.status = status;
        clearReadableSections();
        return this;
    }

    public String getError() {
        return error;
    }

    public FaceInfoResponse setError(String error) {
        this.error = error;
        clearReadableSections();
        return this;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public FaceInfoResponse setStartedAt(String startedAt) {
        this.startedAt = startedAt;
        clearReadableSections();
        return this;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public FaceInfoResponse setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
        clearReadableSections();
        return this;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public FaceInfoResponse setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
        clearReadableSections();
        return this;
    }

    public String getDurationText() {
        return durationText;
    }

    public FaceInfoResponse setDurationText(String durationText) {
        this.durationText = durationText;
        clearReadableSections();
        return this;
    }

    private void clearReadableSections() {
        this.result = null;
        this.candidates = null;
        this.evidence = null;
        this.diagnostics = null;
    }

    private ReadableResultSection buildResultSection() {
        PersonInfo primaryPerson = firstNonNull(person, structuredPrimaryPerson());
        CandidatePersonPortrait primaryPortrait = firstPrimaryPortrait();
        List<SocialAccount> accounts = primaryPerson == null || primaryPerson.getSocialAccounts() == null
                ? List.of()
                : primaryPerson.getSocialAccounts();
        return new ReadableResultSection()
                .setPrimaryPerson(primaryPerson)
                .setPrimaryPortrait(primaryPortrait)
                .setConfirmedSocialAccounts(accounts.stream()
                        .filter(account -> account != null && !isSuspectedSocialAccount(account))
                        .toList());
    }

    private ReadableCandidateSection buildCandidateSection() {
        List<SocialAccount> accounts = person == null || person.getSocialAccounts() == null
                ? List.of()
                : person.getSocialAccounts();
        return new ReadableCandidateSection()
                .setPersonPortraits(firstNonEmpty(candidatePersonPortraits, structuredCandidatePortraits()))
                .setVisionBaselines(firstNonEmpty(visionModelPortraits, structuredVisionPortraits()))
                .setSuspectedSocialAccounts(accounts.stream()
                        .filter(this::isSuspectedSocialAccount)
                        .toList());
    }

    private ReadableEvidenceSection buildEvidenceSection() {
        PersonInfo primaryPerson = firstNonNull(person, structuredPrimaryPerson());
        PortraitSourceReferenceGroup references = structuredPortraits == null ? null : structuredPortraits.getSourceReferences();
        return new ReadableEvidenceSection()
                .setArticles(firstNonEmpty(
                        primaryPerson == null ? List.of() : primaryPerson.getArticleSources(),
                        references == null ? List.of() : references.getWebSources()))
                .setUrls(firstNonEmpty(
                        primaryPerson == null ? List.of() : primaryPerson.getEvidenceUrls(),
                        references == null ? List.of() : references.getEvidenceUrls()))
                .setImages(new ReadableImageEvidence()
                        .setPrimaryMatches(firstNonEmpty(imageMatches, references == null ? List.of() : references.getImageMatches()))
                        .setSupportingMatches(firstNonEmpty(articleImageMatches, references == null ? List.of() : references.getArticleImageMatches())));
    }

    private ReadableDiagnosticsSection buildDiagnosticsSection() {
        return new ReadableDiagnosticsSection()
                .setWarnings(warnings)
                .setError(error)
                .setSelection(selection)
                .setTiming(new ReadableTiming()
                        .setStartedAt(startedAt)
                        .setFinishedAt(finishedAt)
                        .setDurationMs(durationMs)
                        .setDurationText(durationText))
                .setSectionStatus(sectionStatus());
    }

    private Map<String, String> sectionStatus() {
        Map<String, String> statusBySection = new LinkedHashMap<>();
        statusBySection.put("result", getResult().getPrimaryPerson() == null ? "missing" : "available");
        statusBySection.put("candidates", getCandidates().getPersonPortraits().isEmpty()
                && getCandidates().getVisionBaselines().isEmpty() ? "empty" : "available");
        statusBySection.put("evidence", getEvidence().getArticles().isEmpty()
                && getEvidence().getUrls().isEmpty()
                && getEvidence().getImages().getPrimaryMatches().isEmpty()
                && getEvidence().getImages().getSupportingMatches().isEmpty() ? "empty" : "available");
        statusBySection.put("diagnostics", hasDiagnosticsContent() ? "available" : "empty");
        return statusBySection;
    }

    private boolean hasDiagnosticsContent() {
        return !warnings.isEmpty()
                || StringUtils.hasText(error)
                || selection != null
                || startedAt != null
                || finishedAt != null
                || durationMs != null
                || StringUtils.hasText(durationText);
    }

    private CandidatePersonPortrait firstPrimaryPortrait() {
        for (CandidatePersonPortrait portrait : firstNonEmpty(candidatePersonPortraits, structuredCandidatePortraits())) {
            if (portrait != null && portrait.isPrimaryDisplay()) {
                return portrait;
            }
        }
        if (structuredPortraits != null
                && structuredPortraits.getPersonPortraitOne() != null
                && structuredPortraits.getPersonPortraitOne().getCandidatePortraits() != null) {
            for (CandidatePersonPortrait portrait : structuredPortraits.getPersonPortraitOne().getCandidatePortraits()) {
                if (portrait != null && portrait.isPrimaryDisplay()) {
                    return portrait;
                }
            }
        }
        return null;
    }

    private PersonInfo structuredPrimaryPerson() {
        if (structuredPortraits == null || structuredPortraits.getPersonPortraitOne() == null) {
            return null;
        }
        return structuredPortraits.getPersonPortraitOne().getProfile();
    }

    private List<CandidatePersonPortrait> structuredCandidatePortraits() {
        if (structuredPortraits == null || structuredPortraits.getPersonPortraitTwo() == null) {
            return List.of();
        }
        return structuredPortraits.getPersonPortraitTwo().getCandidatePortraits();
    }

    private List<VisionModelPortrait> structuredVisionPortraits() {
        if (structuredPortraits == null || structuredPortraits.getPersonPortraitThree() == null) {
            return List.of();
        }
        return structuredPortraits.getPersonPortraitThree().getVisionModelPortraits();
    }

    private boolean isSuspectedSocialAccount(SocialAccount account) {
        if (account == null) {
            return false;
        }
        if (Boolean.TRUE.equals(account.getSuspected())) {
            return true;
        }
        String confidence = account.getConfidence();
        if (StringUtils.hasText(confidence) && confidence.toLowerCase().contains("suspected")) {
            return true;
        }
        String source = account.getSource();
        return StringUtils.hasText(source) && "maigret".equalsIgnoreCase(source.trim());
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private <T> List<T> firstNonEmpty(List<T> first, List<T> second) {
        return first != null && !first.isEmpty() ? first : (second == null ? List.of() : second);
    }
}
