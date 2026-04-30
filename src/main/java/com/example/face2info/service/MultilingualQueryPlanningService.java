package com.example.face2info.service;

import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.entity.internal.SearchQueryTask;

import java.util.List;

public interface MultilingualQueryPlanningService {

    List<SearchQueryTask> planSecondaryProfileQueries(SearchLanguageProfile profile);

    List<SearchQueryTask> planSectionQueries(SearchLanguageProfile profile, String sectionType, List<String> terms);

}
