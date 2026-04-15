package com.example.face2info.service.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.DerivedTopicType;
import com.example.face2info.entity.internal.TopicRewriteStrategy;
import com.example.face2info.service.TopicRoutingService;
import org.springframework.stereotype.Service;

/**
 * 基于配置决定派生主题查询策略。
 */
@Service
public class TopicRoutingServiceImpl implements TopicRoutingService {

    private final ApiProperties properties;

    public TopicRoutingServiceImpl(ApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public TopicRewriteStrategy resolveStrategy(DerivedTopicRequest request) {
        if (request == null || request.getTopicType() == null) {
            return TopicRewriteStrategy.NORMALIZE;
        }
        String configured = properties.getApi().getQueryRewrite().getTopicStrategies().get(request.getTopicType().getKey());
        if (configured != null) {
            return TopicRewriteStrategy.fromValue(configured);
        }
        return defaultStrategy(request.getTopicType());
    }

    private TopicRewriteStrategy defaultStrategy(DerivedTopicType topicType) {
        return switch (topicType) {
            case POLITICAL_VIEW, CONTROVERSIAL_STATEMENT -> TopicRewriteStrategy.REWRITE;
            case CUSTOM -> TopicRewriteStrategy.DIRECT;
            default -> TopicRewriteStrategy.NORMALIZE;
        };
    }
}
