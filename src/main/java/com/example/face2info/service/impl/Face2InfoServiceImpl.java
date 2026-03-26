package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.RecognitionCandidate;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.PersonInfo;
import com.example.face2info.service.Face2InfoService;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸信息聚合总流程实现。
 * 先识别候选人物，再根据识别结果聚合公开信息并组装统一响应。
 */
@Slf4j
@Service
public class Face2InfoServiceImpl implements Face2InfoService {

    @Autowired
    private ImageUtils imageUtils;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private InformationAggregationService informationAggregationService;

    /**
     * 处理一次完整的人脸信息聚合请求。
     *
     * @param image 上传图片
     * @return 聚合后的结构化结果
     */
    @Override
    public FaceInfoResponse process(MultipartFile image) {
        log.info("Received face2info request");
        imageUtils.validateImage(image);

        // 第一阶段：基于反向搜图识别候选人物。
        RecognitionCandidate candidate = faceRecognitionService.recognize(image);
        if (!StringUtils.hasText(candidate.getName())) {
            return new FaceInfoResponse()
                    .setPerson(null)
                    .setNews(java.util.List.of())
                    .setImageMatches(candidate.getImageMatches())
                    .setStatus("failed")
                    .setError(candidate.getError());
        }

        // 第二阶段：根据候选名称并行聚合人物简介、新闻和社交账号。
        AggregationResult aggregationResult = informationAggregationService.aggregate(candidate.getName());

        // 第三阶段：将内部聚合结果转换为对外响应结构。
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
                .setError(aggregationResult.getErrors().isEmpty() ? null : String.join("；", aggregationResult.getErrors()));
    }
}
