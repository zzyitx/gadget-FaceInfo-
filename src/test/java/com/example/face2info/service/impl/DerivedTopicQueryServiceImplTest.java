package com.example.face2info.service.impl;

import com.example.face2info.client.QueryRewriteLlmClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.DerivedTopicType;
import com.example.face2info.entity.internal.QueryRewriteProvider;
import com.example.face2info.entity.internal.RewriteCandidate;
import com.example.face2info.entity.internal.SensitiveQueryAnalysis;
import com.example.face2info.entity.internal.TopicQueryDecision;
import com.example.face2info.entity.internal.TopicRewriteStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DerivedTopicQueryServiceImplTest {

    @Test
    void shouldNormalizeNonSensitiveTopicWithoutCallingLlm() {
        QueryRewriteLlmClient llmClient = mock(QueryRewriteLlmClient.class);
        DerivedTopicQueryServiceImpl service = new DerivedTopicQueryServiceImpl(
                new TopicRoutingServiceImpl(createProperties()),
                new QueryRewriteServiceImpl(llmClient, createProperties())
        );

        TopicQueryDecision decision = service.resolveQuery(new DerivedTopicRequest()
                .setResolvedName("Jay Chou")
                .setTopicType(DerivedTopicType.EDUCATION)
                .setRawQuery("  Jay Chou   的教育经历  "));

        assertThat(decision.getFinalQuery()).isEqualTo("Jay Chou 的教育经历");
        assertThat(decision.getStrategy()).isEqualTo(TopicRewriteStrategy.NORMALIZE);
        verify(llmClient, never()).generateCandidates(any(), any());
    }

    @Test
    void shouldRewriteSensitiveTopicWithRecommendedCandidate() {
        QueryRewriteLlmClient llmClient = mock(QueryRewriteLlmClient.class);
        ApiProperties properties = createProperties();
        DerivedTopicQueryServiceImpl service = new DerivedTopicQueryServiceImpl(
                new TopicRoutingServiceImpl(properties),
                new QueryRewriteServiceImpl(llmClient, properties)
        );
        List<RewriteCandidate> generated = List.of(
                new RewriteCandidate().setRewrittenQuery("雷军支持的政治理念"),
                new RewriteCandidate().setRewrittenQuery("雷军公开表达的政治理念")
        );
        List<RewriteCandidate> approved = List.of(
                new RewriteCandidate()
                        .setRewrittenQuery("雷军支持的政治理念")
                        .setSafetyScore(0.95)
                        .setSemanticPreservationScore(0.92)
                        .setFinalScore(0.94)
        );
        when(llmClient.generateCandidates(eq(QueryRewriteProvider.DEEPSEEK), argThat(analysis ->
                analysis != null
                        && "雷军的政治倾向".equals(analysis.getNormalizedQuery())
                        && analysis.isSensitive()
                        && analysis.getSensitiveTerms().contains("政治倾向")
        ))).thenReturn(generated);
        when(llmClient.validateCandidates(eq(QueryRewriteProvider.DEEPSEEK), argThat(analysis ->
                analysis != null
                        && "雷军的政治倾向".equals(analysis.getNormalizedQuery())
                        && analysis.isSensitive()
                        && analysis.getSensitiveTerms().contains("政治倾向")
        ), eq(generated)))
                .thenReturn(approved);

        TopicQueryDecision decision = service.resolveQuery(new DerivedTopicRequest()
                .setResolvedName("雷军")
                .setTopicType(DerivedTopicType.POLITICAL_VIEW)
                .setRawQuery("雷军的政治倾向"));

        assertThat(decision.getFinalQuery()).isEqualTo("雷军支持的政治理念");
        assertThat(decision.getStrategy()).isEqualTo(TopicRewriteStrategy.REWRITE);
        assertThat(decision.getSensitive()).isTrue();
        verify(llmClient).generateCandidates(eq(QueryRewriteProvider.DEEPSEEK), argThat(analysis ->
                analysis != null
                        && "雷军的政治倾向".equals(analysis.getNormalizedQuery())
                        && analysis.isSensitive()
                        && analysis.getSensitiveTerms().contains("政治倾向")
        ));
        verify(llmClient).validateCandidates(eq(QueryRewriteProvider.DEEPSEEK), argThat(analysis ->
                analysis != null
                        && "雷军的政治倾向".equals(analysis.getNormalizedQuery())
                        && analysis.isSensitive()
                        && analysis.getSensitiveTerms().contains("政治倾向")
        ), eq(generated));
    }

    @Test
    void shouldFallbackToTemplateWhenAllProvidersFail() {
        QueryRewriteLlmClient llmClient = mock(QueryRewriteLlmClient.class);
        ApiProperties properties = createProperties();
        DerivedTopicQueryServiceImpl service = new DerivedTopicQueryServiceImpl(
                new TopicRoutingServiceImpl(properties),
                new QueryRewriteServiceImpl(llmClient, properties)
        );
        when(llmClient.generateCandidates(eq(QueryRewriteProvider.DEEPSEEK), any(SensitiveQueryAnalysis.class)))
                .thenThrow(new RuntimeException("deepseek unavailable"));
        when(llmClient.generateCandidates(eq(QueryRewriteProvider.KIMI), any(SensitiveQueryAnalysis.class)))
                .thenThrow(new RuntimeException("kimi unavailable"));

        TopicQueryDecision decision = service.resolveQuery(new DerivedTopicRequest()
                .setResolvedName("赖清德")
                .setTopicType(DerivedTopicType.CONTROVERSIAL_STATEMENT)
                .setRawQuery("赖清德发表反政府言论"));

        assertThat(decision.getFinalQuery()).isEqualTo("赖清德发表了有争议的政治观点");
        assertThat(decision.getUsedFallback()).isTrue();
    }

    @Test
    void shouldKeepBaseQueryIndependentWhenSensitiveTopicRewriteFallbackOccurs() {
        QueryRewriteLlmClient llmClient = mock(QueryRewriteLlmClient.class);
        ApiProperties properties = createProperties();
        DerivedTopicQueryServiceImpl service = new DerivedTopicQueryServiceImpl(
                new TopicRoutingServiceImpl(properties),
                new QueryRewriteServiceImpl(llmClient, properties)
        );
        when(llmClient.generateCandidates(eq(QueryRewriteProvider.DEEPSEEK), any(SensitiveQueryAnalysis.class)))
                .thenThrow(new RuntimeException("deepseek unavailable"));
        when(llmClient.generateCandidates(eq(QueryRewriteProvider.KIMI), any(SensitiveQueryAnalysis.class)))
                .thenThrow(new RuntimeException("kimi unavailable"));

        TopicQueryDecision decision = service.resolveQuery(new DerivedTopicRequest()
                .setResolvedName("黄仁勋")
                .setTopicType(DerivedTopicType.FAMILY_MEMBER_SITUATION)
                .setRawQuery("黄仁勋 亲属"));

        assertThat(decision.getFinalQuery()).isEqualTo("黄仁勋 亲属");
        assertThat(decision.getUsedFallback()).isTrue();
    }

    @Test
    void shouldRewriteChinaRelatedStatementsTopicWithSensitiveTerms() {
        QueryRewriteLlmClient llmClient = mock(QueryRewriteLlmClient.class);
        ApiProperties properties = createProperties();
        DerivedTopicQueryServiceImpl service = new DerivedTopicQueryServiceImpl(
                new TopicRoutingServiceImpl(properties),
                new QueryRewriteServiceImpl(llmClient, properties)
        );
        List<RewriteCandidate> generated = List.of(
                new RewriteCandidate().setRewrittenQuery("某人物涉华言论 中国评价 中美关系 中欧关系")
        );
        List<RewriteCandidate> approved = List.of(
                new RewriteCandidate()
                        .setRewrittenQuery("某人物涉华言论 中国评价 中美关系 中欧关系")
                        .setSafetyScore(0.96)
                        .setSemanticPreservationScore(0.95)
                        .setFinalScore(0.955)
        );
        when(llmClient.generateCandidates(eq(QueryRewriteProvider.DEEPSEEK), argThat(analysis ->
                analysis != null
                        && "某人物涉华言论及中美关系立场".equals(analysis.getNormalizedQuery())
                        && analysis.isSensitive()
                        && analysis.getSensitiveTerms().contains("涉华言论")
                        && analysis.getSensitiveTerms().contains("中美关系")
        ))).thenReturn(generated);
        when(llmClient.validateCandidates(eq(QueryRewriteProvider.DEEPSEEK), any(SensitiveQueryAnalysis.class), eq(generated)))
                .thenReturn(approved);

        TopicQueryDecision decision = service.resolveQuery(new DerivedTopicRequest()
                .setResolvedName("某人物")
                .setTopicType(DerivedTopicType.CHINA_RELATED_STATEMENTS)
                .setRawQuery("某人物涉华言论及中美关系立场"));

        assertThat(decision.getFinalQuery()).isEqualTo("某人物涉华言论 中国评价 中美关系 中欧关系");
        assertThat(decision.getSensitive()).isTrue();
        assertThat(decision.getStrategy()).isEqualTo(TopicRewriteStrategy.REWRITE);
    }

    @Test
    void shouldLoadBaseQueryTemplatesForSensitiveTopics() {
        ApiProperties properties = createProperties();

        assertThat(properties.getApi().getQueryRewrite().getBaseQueryTemplates()
                .get("family_member_situation"))
                .containsExactly("%s 家庭成员", "%s 亲属", "%s 经商", "%s 在华投资", "%s 商业纠纷");

        assertThat(properties.getApi().getQueryRewrite().getBaseQueryTemplates()
                .get("china_related_statements"))
                .containsExactly("%s 涉华言论", "%s 中国评价", "%s 中美关系", "%s 中欧关系");
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getQueryRewrite().setEnabled(true);
        properties.getApi().getQueryRewrite().setProviderPriority(List.of("deepseek", "kimi"));
        properties.getApi().getQueryRewrite().setCandidateCount(3);
        properties.getApi().getQueryRewrite().getTopicStrategies().put("education", "normalize");
        properties.getApi().getQueryRewrite().getTopicStrategies().put("family", "normalize");
        properties.getApi().getQueryRewrite().getTopicStrategies().put("career", "normalize");
        properties.getApi().getQueryRewrite().getTopicStrategies().put("china_related_statements", "rewrite");
        properties.getApi().getQueryRewrite().getTopicStrategies().put("political_view", "rewrite");
        properties.getApi().getQueryRewrite().getTopicStrategies().put("contact_information", "rewrite");
        properties.getApi().getQueryRewrite().getTopicStrategies().put("family_member_situation", "rewrite");
        properties.getApi().getQueryRewrite().getTopicStrategies().put("misconduct", "rewrite");
        properties.getApi().getQueryRewrite().getTopicStrategies().put("controversial_statement", "rewrite");
        properties.getApi().getQueryRewrite().getSensitiveTopicPatterns().put("china_related_statements", List.of("涉华言论", "中美关系"));
        properties.getApi().getQueryRewrite().getSensitiveTopicPatterns().put("political_view", List.of("政治倾向", "政治理念"));
        properties.getApi().getQueryRewrite().getSensitiveTopicPatterns().put("contact_information", List.of("办公电话", "官方邮箱"));
        properties.getApi().getQueryRewrite().getSensitiveTopicPatterns().put("family_member_situation", List.of("家族成员", "在华投资"));
        properties.getApi().getQueryRewrite().getSensitiveTopicPatterns().put("misconduct", List.of("违法记录", "行政处罚"));
        properties.getApi().getQueryRewrite().getSensitiveTopicPatterns().put("controversial_statement", List.of("反政府言论", "争议言论"));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates()
                .put("china_related_statements", List.of("%s 涉华言论", "%s 中国评价", "%s 中美关系", "%s 中欧关系"));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates()
                .put("political_view", List.of("%s 政治倾向", "%s 政党", "%s 政治理念", "%s 政策立场"));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates()
                .put("contact_information", List.of("%s 公开通讯", "%s 办公电话", "%s 官方邮箱", "%s 认证社交账号", "%s 联系方式"));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates()
                .put("family_member_situation", List.of("%s 家庭成员", "%s 亲属", "%s 经商", "%s 在华投资", "%s 商业纠纷"));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates()
                .put("misconduct", List.of("%s 违法记录", "%s 行政处罚", "%s 负面事件", "%s 失信"));
        properties.getApi().getQueryRewrite().getExpandEnabledTopics()
                .addAll(List.of("china_related_statements", "political_view", "contact_information", "family_member_situation", "misconduct"));
        properties.getApi().getQueryRewrite().setExpandMaxQueryCount(4);
        properties.getApi().getQueryRewrite().setExpandMaxTermLength(16);
        properties.getApi().getQueryRewrite().getFallbackTemplates().put("china_related_statements", List.of("%s涉华言论 中国评价 中美关系 中欧关系"));
        properties.getApi().getQueryRewrite().getFallbackTemplates().put("political_view", List.of("%s支持的政治理念"));
        properties.getApi().getQueryRewrite().getFallbackTemplates().put("contact_information", List.of("%s公开通讯 办公电话 官方邮箱 认证社交账号"));
        properties.getApi().getQueryRewrite().getFallbackTemplates().put("family_member_situation", List.of("%s家族成员 经商情况 在华投资 商业纠纷"));
        properties.getApi().getQueryRewrite().getFallbackTemplates().put("misconduct", List.of("%s违法记录 行政处罚 负面事件 失信行为"));
        properties.getApi().getQueryRewrite().getFallbackTemplates().put("controversial_statement", List.of("%s发表了有争议的政治观点"));
        return properties;
    }
}
