package com.example.face2info.service.impl;

import com.example.face2info.client.FaceCheckClient;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.FaceCheckMatchCandidate;
import com.example.face2info.entity.internal.FaceCheckSearchResponse;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceCheckMatch;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.PersonInfo;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.service.Face2InfoService;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class Face2InfoServiceImpl implements Face2InfoService {

    private final ImageUtils imageUtils;
    private final FaceRecognitionService faceRecognitionService;
    private final InformationAggregationService informationAggregationService;
    private final FaceCheckClient faceCheckClient;

    public Face2InfoServiceImpl(ImageUtils imageUtils,
                                FaceRecognitionService faceRecognitionService,
                                InformationAggregationService informationAggregationService,
                                FaceCheckClient faceCheckClient) {
        this.imageUtils = imageUtils;
        this.faceRecognitionService = faceRecognitionService;
        this.informationAggregationService = informationAggregationService;
        this.faceCheckClient = faceCheckClient;
    }

    @Override
    public FaceInfoResponse process(MultipartFile image) {
        log.info("总流程开始 fileName={} size={}", image.getOriginalFilename(), image.getSize());
        imageUtils.validateImage(image);

        RecognitionEvidence evidence = faceRecognitionService.recognize(image);
        log.info("识别阶段完成 imageMatchCount={} seedQueryCount={} webEvidenceCount={} errorCount={}",
                evidence.getImageMatches().size(),
                evidence.getSeedQueries().size(),
                evidence.getWebEvidences().size(),
                evidence.getErrors().size());

        AggregationResult aggregationResult = informationAggregationService.aggregate(evidence);
        log.info("聚合阶段完成 warningCount={} errorCount={} socialCount={} newsCount={} resolvedName={}",
                aggregationResult.getWarnings().size(),
                aggregationResult.getErrors().size(),
                aggregationResult.getSocialAccounts().size(),
                aggregationResult.getNews().size(),
                aggregationResult.getPerson() == null ? null : aggregationResult.getPerson().getName());

        List<String> combinedErrors = new ArrayList<>(aggregationResult.getErrors());
        List<String> warnings = new ArrayList<>(aggregationResult.getWarnings());
        List<FaceCheckMatch> facecheckMatches = new ArrayList<>();
        try {
            FaceCheckSearchResponse faceCheckResponse = faceCheckClient.search(image);
            if (faceCheckResponse.isTimedOut()) {
                warnings.add("FaceCheck 搜索超时");
            }
            facecheckMatches = faceCheckResponse.getItems().stream()
                    .map(this::toFacecheckMatch)
                    .toList();
        } catch (ApiCallException ex) {
            log.warn("FaceCheck 匹配阶段失败 message={}", ex.getMessage());
            combinedErrors.add(ex.getMessage());
        }

        if (aggregationResult.getPerson() == null || !StringUtils.hasText(aggregationResult.getPerson().getName())) {
            List<String> errors = new ArrayList<>(evidence.getErrors());
            errors.addAll(combinedErrors);
            log.warn("总流程失败 errorCount={} errors={}", errors.size(), errors);
            return new FaceInfoResponse()
                    .setPerson(null)
                    .setNews(aggregationResult.getNews())
                    .setImageMatches(evidence.getImageMatches())
                    .setFacecheckMatches(facecheckMatches)
                    .setStatus("failed")
                    .setError(errors.isEmpty() ? "Unable to resolve person information." : String.join("; ", errors));
        }

        PersonInfo person = new PersonInfo()
                .setName(aggregationResult.getPerson().getName())
                .setDescription(aggregationResult.getPerson().getDescription())
                .setSummary(aggregationResult.getPerson().getSummary())
                .setTags(aggregationResult.getPerson().getTags())
                .setWikipedia(aggregationResult.getPerson().getWikipedia())
                .setOfficialWebsite(aggregationResult.getPerson().getOfficialWebsite())
                .setSocialAccounts(aggregationResult.getSocialAccounts());

        String status = (!combinedErrors.isEmpty() || !warnings.isEmpty()) ? "partial" : "success";
        log.info("总流程结束 status={} personName={} warningCount={} errorCount={}",
                status, person.getName(), warnings.size(), combinedErrors.size());
        return new FaceInfoResponse()
                .setPerson(person)
                .setNews(aggregationResult.getNews())
                .setWarnings(warnings)
                .setImageMatches(evidence.getImageMatches())
                .setFacecheckMatches(facecheckMatches)
                .setStatus(status)
                .setError(combinedErrors.isEmpty() ? null : String.join("; ", combinedErrors));
    }

    private FaceCheckMatch toFacecheckMatch(FaceCheckMatchCandidate candidate) {
        return new FaceCheckMatch()
                .setImageDataUrl(candidate.getImageDataUrl())
                .setSimilarityScore(candidate.getSimilarityScore())
                .setSourceHost(candidate.getSourceHost())
                .setSourceUrl(candidate.getSourceUrl())
                .setGroup(candidate.getGroup())
                .setSeen(candidate.getSeen())
                .setIndex(candidate.getIndex());
    }
}
