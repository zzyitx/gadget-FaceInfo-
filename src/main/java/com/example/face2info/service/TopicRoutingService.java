package com.example.face2info.service;

import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.TopicRewriteStrategy;

/**
 * 派生主题查询路由服务。
 */
public interface TopicRoutingService {

    TopicRewriteStrategy resolveStrategy(DerivedTopicRequest request);
}
