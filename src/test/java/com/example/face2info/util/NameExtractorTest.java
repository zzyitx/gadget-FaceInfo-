package com.example.face2info.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameExtractorTest {

    private final NameExtractor extractor = new NameExtractor();

    @Test
    void shouldRejectGenericNewsLabelAsCandidateName() {
        assertThat(extractor.cleanCandidateName("News")).isNull();
        assertThat(extractor.cleanCandidateName("Example News")).isNull();
    }

    @Test
    void shouldRejectDailyPageAndMediaWordsAsCandidateName() {
        assertThat(extractor.cleanCandidateName("Home")).isNull();
        assertThat(extractor.cleanCandidateName("Search Results")).isNull();
        assertThat(extractor.cleanCandidateName("Latest Articles")).isNull();
        assertThat(extractor.cleanCandidateName("Media Gallery")).isNull();
        assertThat(extractor.cleanCandidateName("World News")).isNull();
        assertThat(extractor.cleanCandidateName("Official Website")).isNull();
    }

    @Test
    void shouldKeepPersonNameWhenTitleContainsNewsNoise() {
        assertThat(extractor.cleanCandidateName("Jay Chou News")).isEqualTo("Jay Chou");
        assertThat(extractor.cleanCandidateName("Official profile - Jensen Huang")).isEqualTo("Jensen Huang");
        assertThat(extractor.cleanCandidateName("Ada Lovelace image")).isEqualTo("Ada Lovelace");
        assertThat(extractor.cleanCandidateName("雷军 新闻")).isEqualTo("雷军");
    }
}
