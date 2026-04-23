package com.example.face2info.service;

import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import org.springframework.lang.Nullable;

import java.util.List;

public interface PrimarySearchQueryBuilder {

    List<String> buildSecondaryProfileQueries(String resolvedName,
                                             SearchLanguageProfile languageProfile,
                                             @Nullable ResolvedPersonProfile profile);

    List<String> buildSectionQueries(String resolvedName,
                                     SearchLanguageProfile languageProfile,
                                     @Nullable ResolvedPersonProfile profile,
                                     String sectionType);
}
