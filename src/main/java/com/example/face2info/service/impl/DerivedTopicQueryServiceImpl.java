package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.TopicQueryDecision;
import com.example.face2info.entity.internal.TopicRewriteStrategy;
import com.example.face2info.service.DerivedTopicQueryService;
import com.example.face2info.service.QueryRewriteService;
import com.example.face2info.service.TopicRoutingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 派生主题查询统一入口，负责把不同主题分发到对应策略。
 */
@Service
public class DerivedTopicQueryServiceImpl implements DerivedTopicQueryService {

    private final TopicRoutingService topicRoutingService;
    private final QueryRewriteService queryRewriteService;

    public DerivedTopicQueryServiceImpl(TopicRoutingService topicRoutingService,
                                        QueryRewriteService queryRewriteService) {
        this.topicRoutingService = topicRoutingService;
        this.queryRewriteService = queryRewriteService;
    }

    @Override
    public TopicQueryDecision resolveQuery(DerivedTopicRequest request) {
        TopicRewriteStrategy strategy = topicRoutingService.resolveStrategy(request);
        String normalized = normalizeQuery(request == null ? null : request.getRawQuery());
        if (strategy == TopicRewriteStrategy.DIRECT) {
            return new TopicQueryDecision()
                    .setFinalQuery(normalized)
                    .setStrategy(TopicRewriteStrategy.DIRECT);
        }
        if (strategy == TopicRewriteStrategy.NORMALIZE) {
            return new TopicQueryDecision()
                    .setFinalQuery(normalized)
                    .setStrategy(TopicRewriteStrategy.NORMALIZE);
        }
        DerivedTopicRequest normalizedRequest = new DerivedTopicRequest()
                .setResolvedName(request == null ? null : request.getResolvedName())
                .setTopicType(request == null ? null : request.getTopicType())
                .setProtectedTerms(request == null ? null : request.getProtectedTerms())
                .setRawQuery(normalized);
        return queryRewriteService.rewrite(normalizedRequest);
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return query;
        }
        return query.trim().replaceAll("\\s+", " ");
    }
}
