package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.DerivedTopicType;
import com.example.face2info.entity.internal.TopicQueryDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DerivedTopicQueryServiceImplTest {

    private final DerivedTopicQueryServiceImpl service = new DerivedTopicQueryServiceImpl();

    @Test
    void shouldNormalizeEducationTopicWithoutRewrite() {
        TopicQueryDecision decision = service.resolveQuery(new DerivedTopicRequest()
                .setResolvedName("Jay Chou")
                .setTopicType(DerivedTopicType.EDUCATION)
                .setRawQuery("  Jay Chou   的教育经历  "));

        assertThat(decision.getFinalQuery()).isEqualTo("Jay Chou 的教育经历");
        assertThat(decision.getSensitive()).isFalse();
        assertThat(decision.getUsedFallback()).isFalse();
    }

    @Test
    void shouldKeepSensitiveTopicQueryWithoutCallingRewriteChain() {
        TopicQueryDecision decision = service.resolveQuery(new DerivedTopicRequest()
                .setResolvedName("雷军")
                .setTopicType(DerivedTopicType.POLITICAL_VIEW)
                .setRawQuery(" 雷军   政治倾向 "));

        assertThat(decision.getFinalQuery()).isEqualTo("雷军 政治倾向");
        assertThat(decision.getSensitive()).isFalse();
        assertThat(decision.getUsedFallback()).isFalse();
    }

    @Test
    void shouldReturnBlankDecisionForBlankRawQuery() {
        TopicQueryDecision decision = service.resolveQuery(new DerivedTopicRequest()
                .setTopicType(DerivedTopicType.CONTACT_INFORMATION)
                .setRawQuery("   "));

        assertThat(decision.getFinalQuery()).isBlank();
    }
}
