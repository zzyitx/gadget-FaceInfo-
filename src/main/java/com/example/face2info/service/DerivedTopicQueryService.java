package com.example.face2info.service;

import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.TopicQueryDecision;

/**
 * 派生主题查询编排入口。
 */
public interface DerivedTopicQueryService {

    TopicQueryDecision resolveQuery(DerivedTopicRequest request);
}
