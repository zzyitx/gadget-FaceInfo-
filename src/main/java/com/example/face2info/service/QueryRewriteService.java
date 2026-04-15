package com.example.face2info.service;

import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.TopicQueryDecision;

/**
 * 敏感主题查询改写服务。
 */
public interface QueryRewriteService {

    TopicQueryDecision rewrite(DerivedTopicRequest request);
}
