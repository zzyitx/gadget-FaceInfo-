package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.TopicQueryDecision;
import com.example.face2info.service.DerivedTopicQueryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 派生主题查询统一入口，负责把不同主题分发到对应策略。
 */
@Service
public class DerivedTopicQueryServiceImpl implements DerivedTopicQueryService {

    @Override
    public TopicQueryDecision resolveQuery(DerivedTopicRequest request) {
        String normalized = normalizeQuery(request == null ? null : request.getRawQuery());
        return new TopicQueryDecision()
                .setFinalQuery(normalized)
                .setSensitive(false)
                .setUsedFallback(false);
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return query;
        }
        return query.trim().replaceAll("\\s+", " ");
    }
}
