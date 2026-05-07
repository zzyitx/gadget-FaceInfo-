package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOsintSocialAccountClientImplTest {

    @Test
    void shouldSkipLaterCandidatesWhenExecutableIsMissing() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getMaigret().setEnabled(true);
        properties.getApi().getMaigret().setExecutable("face2info-missing-maigret-command");
        properties.getApi().getMaigret().getSherlock().setEnabled(false);
        properties.getApi().getMaigret().getTookie().setEnabled(false);
        LocalOsintSocialAccountClientImpl client = new LocalOsintSocialAccountClientImpl(properties, new ObjectMapper());

        assertThat(client.findSuspectedAccounts("成龙")).isEmpty();
        assertThat(client.findSuspectedAccounts("EyeOfJackieChan")).isEmpty();
    }
}
