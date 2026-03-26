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

/**
 * 人脸信息聚合总流程实现。
 */
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
        log.info("Received face2info request");
        imageUtils.validateImage(image);

        RecognitionEvidence evidence = faceRecognitionService.recognize(image);
        AggregationResult aggregationResult = informationAggregationService.aggregate(evidence);
        if (aggregationResult.getPerson() == null || !StringUtils.hasText(aggregationResult.getPerson().getName())) {
            List<String> errors = new ArrayList<>(evidence.getErrors());
            errors.addAll(aggregationResult.getErrors());
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
                .setWikipedia(aggregationResult.getPerson().getWikipedia())
                .setOfficialWebsite(aggregationResult.getPerson().getOfficialWebsite())
                .setSocialAccounts(aggregationResult.getSocialAccounts());

        return new FaceInfoResponse()
                .setPerson(person)
                .setNews(aggregationResult.getNews())
                .setImageMatches(evidence.getImageMatches())
                .setStatus(aggregationResult.getErrors().isEmpty() ? "success" : "partial")
                .setError(aggregationResult.getErrors().isEmpty() ? null : String.join("; ", aggregationResult.getErrors()));
    }
}
