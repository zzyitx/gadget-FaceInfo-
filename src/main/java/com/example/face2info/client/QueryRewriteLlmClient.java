package com.example.face2info.client;

import com.example.face2info.entity.internal.QueryRewriteProvider;
import com.example.face2info.entity.internal.RewriteCandidate;
import com.example.face2info.entity.internal.SensitiveQueryAnalysis;

import java.util.List;

/**
 * 派生主题查询改写大模型客户端。
 */
public interface QueryRewriteLlmClient {

    List<RewriteCandidate> generateCandidates(QueryRewriteProvider provider, SensitiveQueryAnalysis analysis);

    List<RewriteCandidate> validateCandidates(QueryRewriteProvider provider,
                                              SensitiveQueryAnalysis analysis,
                                              List<RewriteCandidate> candidates);
}
