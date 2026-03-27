package com.example.face2info.service;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.RecognitionEvidence;

/**
 * 信息聚合服务。
 * 根据候选名称并行拉取人物简介、社交账号和新闻信息。
 */
public interface InformationAggregationService {

    /**
     * 聚合指定人物名称的公开信息。
     *
     * @param name 候选人物名称
     * @return 聚合结果
     */
    AggregationResult aggregate(RecognitionEvidence evidence);
}
