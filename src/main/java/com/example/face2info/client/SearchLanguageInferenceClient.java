package com.example.face2info.client;

import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageInferenceResult;

/**
 * 抽象搜索语言推断调用。
 */
public interface SearchLanguageInferenceClient {

    SearchLanguageInferenceResult inferSearchLanguageProfile(String resolvedName,
                                                             ResolvedPersonProfile profile);
}
