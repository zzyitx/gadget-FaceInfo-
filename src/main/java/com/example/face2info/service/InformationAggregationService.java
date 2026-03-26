package com.example.face2info.service;

import com.example.face2info.entity.internal.AggregationResult;

/**
 * 信息聚合服务抽象。
 */
public interface InformationAggregationService {

    AggregationResult aggregate(String name);
}
