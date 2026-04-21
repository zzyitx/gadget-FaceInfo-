package com.example.face2info.service;

import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;

public interface SearchLanguageProfileService {

    SearchLanguageProfile resolveProfile(String resolvedName, ResolvedPersonProfile profile);
}
