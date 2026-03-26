package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.RecognitionCandidate;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.PersonInfo;
import com.example.face2info.service.Face2InfoService;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class Face2InfoServiceImpl implements Face2InfoService {

    private static final Logger log = LoggerFactory.getLogger(Face2InfoServiceImpl.class);

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

        RecognitionCandidate candidate = faceRecognitionService.recognize(image);
        if (!StringUtils.hasText(candidate.getName())) {
            return new FaceInfoResponse()
                    .setPerson(null)
                    .setNews(java.util.List.of())
                    .setImageMatches(candidate.getImageMatches())
                    .setStatus("failed")
                    .setError(candidate.getError());
        }

        AggregationResult aggregationResult = informationAggregationService.aggregate(candidate.getName());

        PersonInfo person = new PersonInfo()
                .setName(aggregationResult.getPerson().getName())
                .setDescription(aggregationResult.getPerson().getDescription())
                .setWikipedia(aggregationResult.getPerson().getWikipedia())
                .setOfficialWebsite(aggregationResult.getPerson().getOfficialWebsite())
                .setSocialAccounts(aggregationResult.getSocialAccounts());

        return new FaceInfoResponse()
                .setPerson(person)
                .setNews(aggregationResult.getNews())
                .setImageMatches(candidate.getImageMatches())
                .setStatus(aggregationResult.getErrors().isEmpty() ? "success" : "partial")
                .setError(aggregationResult.getErrors().isEmpty() ? null : String.join("��", aggregationResult.getErrors()));
    }
}
