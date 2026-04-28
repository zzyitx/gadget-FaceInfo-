package com.example.face2info.service;

import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

public interface SearchTemplateQueryBuilder {

    List<String> build(String topicKey,
                       String resolvedName,
                       SearchLanguageProfile languageProfile,
                       @Nullable ResolvedPersonProfile profile,
                       Map<String, String> extraVariables);
}
