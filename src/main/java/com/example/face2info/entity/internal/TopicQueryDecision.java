package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 派生主题查询决策结果。
 */
public class TopicQueryDecision {

    private String finalQuery;
    private TopicRewriteStrategy strategy = TopicRewriteStrategy.NORMALIZE;
    private boolean sensitive;
    private boolean usedFallback;
    private List<String> tokens = new ArrayList<>();

    public String getFinalQuery() {
        return finalQuery;
    }

    public TopicQueryDecision setFinalQuery(String finalQuery) {
        this.finalQuery = finalQuery;
        return this;
    }

    public TopicRewriteStrategy getStrategy() {
        return strategy;
    }

    public TopicQueryDecision setStrategy(TopicRewriteStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    public boolean getSensitive() {
        return sensitive;
    }

    public TopicQueryDecision setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
        return this;
    }

    public boolean getUsedFallback() {
        return usedFallback;
    }

    public TopicQueryDecision setUsedFallback(boolean usedFallback) {
        this.usedFallback = usedFallback;
        return this;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public TopicQueryDecision setTokens(List<String> tokens) {
        this.tokens = tokens == null ? new ArrayList<>() : tokens;
        return this;
    }
}
