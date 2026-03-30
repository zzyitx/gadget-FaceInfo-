package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.PersonInfo;
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

    public Face2InfoServiceImpl(ImageUtils imageUtils,
                                FaceRecognitionService faceRecognitionService,
                                InformationAggregationService informationAggregationService) {
        this.imageUtils = imageUtils;
        this.faceRecognitionService = faceRecognitionService;
        this.informationAggregationService = informationAggregationService;
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
        if (aggregationResult.getPerson() == null || !StringUtils.hasText(aggregationResult.getPerson().getName())) {
            List<String> errors = new ArrayList<>(evidence.getErrors());
            errors.addAll(aggregationResult.getErrors());
            log.warn("总流程失败 errorCount={} errors={}", errors.size(), errors);
            return new FaceInfoResponse()
                    .setPerson(null)
                    .setNews(aggregationResult.getNews())
                    .setImageMatches(evidence.getImageMatches())
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

        String status = aggregationResult.getErrors().isEmpty() ? "success" : "partial";
        log.info("总流程结束 status={} personName={} warningCount={} errorCount={}",
                status, person.getName(), aggregationResult.getWarnings().size(), aggregationResult.getErrors().size());
        return new FaceInfoResponse()
                .setPerson(person)
                .setNews(aggregationResult.getNews())
                .setWarnings(aggregationResult.getWarnings())
                .setImageMatches(evidence.getImageMatches())
                .setStatus(status)
                .setError(aggregationResult.getErrors().isEmpty() ? null : String.join("; ", aggregationResult.getErrors()));
    }
}
