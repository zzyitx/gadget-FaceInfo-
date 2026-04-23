package com.example.face2info.service;

import com.example.face2info.entity.internal.DigitalFootprintQuery;
import com.example.face2info.entity.internal.SearchLanguageProfile;

import java.util.List;

public interface DigitalFootprintQueryBuilder {

    List<DigitalFootprintQuery> build(String resolvedName, SearchLanguageProfile languageProfile);
}
