package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.PersonBasicInfoResponse;
import com.example.face2info.entity.response.PersonInfo;
import com.example.face2info.service.Face2InfoService;
import com.example.face2info.service.FaceDetectionService;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import com.example.face2info.util.InMemoryMultipartFile;
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
    private final FaceDetectionService faceDetectionService;

    public Face2InfoServiceImpl(ImageUtils imageUtils,
                                FaceRecognitionService faceRecognitionService,
                                InformationAggregationService informationAggregationService,
                                FaceDetectionService faceDetectionService) {
        this.imageUtils = imageUtils;
        this.faceRecognitionService = faceRecognitionService;
        this.informationAggregationService = informationAggregationService;
        this.faceDetectionService = faceDetectionService;
    }

    @Override
    public FaceInfoResponse process(MultipartFile image) {
        return processRecognizedImage(image);
    }

    @Override
    public FaceInfoResponse processSelectedFace(String detectionId, String faceId) {
        SelectedFaceCrop crop = faceDetectionService.getSelectedFaceCrop(detectionId, faceId);
        MultipartFile selectedFace = new InMemoryMultipartFile(crop.getFilename(), crop.getContentType(), crop.getBytes());
        return processRecognizedImage(selectedFace);
    }

    private FaceInfoResponse processRecognizedImage(MultipartFile image) {
        log.info("总流程开始 fileName={} size={}", image.getOriginalFilename(), image.getSize());
        imageUtils.validateImage(image);

        RecognitionEvidence evidence = faceRecognitionService.recognize(image);
        AggregationResult aggregationResult = informationAggregationService.aggregate(evidence);

        List<String> combinedErrors = new ArrayList<>(aggregationResult.getErrors());
        List<String> warnings = new ArrayList<>(aggregationResult.getWarnings());

        if (aggregationResult.getPerson() == null || !StringUtils.hasText(aggregationResult.getPerson().getName())) {
            List<String> errors = new ArrayList<>(evidence.getErrors());
            errors.addAll(combinedErrors);
            return new FaceInfoResponse()
                    .setPerson(null)
                    .setNews(aggregationResult.getNews())
                    .setImageMatches(evidence.getImageMatches())
                    .setWarnings(warnings)
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
                .setBasicInfo(toResponseBasicInfo(aggregationResult.getPerson().getBasicInfo()))
                .setSocialAccounts(aggregationResult.getSocialAccounts());

        String status = (!combinedErrors.isEmpty() || !warnings.isEmpty()) ? "partial" : "success";
        return new FaceInfoResponse()
                .setPerson(person)
                .setNews(aggregationResult.getNews())
                .setWarnings(warnings)
                .setImageMatches(evidence.getImageMatches())
                .setStatus(status)
                .setError(combinedErrors.isEmpty() ? null : String.join("; ", combinedErrors));
    }

    private PersonBasicInfoResponse toResponseBasicInfo(PersonBasicInfo basicInfo) {
        if (basicInfo == null) {
            return new PersonBasicInfoResponse();
        }
        return new PersonBasicInfoResponse()
                .setBirthDate(basicInfo.getBirthDate())
                .setEducation(basicInfo.getEducation())
                .setOccupations(basicInfo.getOccupations())
                .setBiographies(basicInfo.getBiographies());
    }
}
