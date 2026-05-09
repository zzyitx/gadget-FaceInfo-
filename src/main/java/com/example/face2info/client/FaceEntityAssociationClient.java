package com.example.face2info.client;

import com.example.face2info.entity.internal.FaceEntityAssociation;
import com.example.face2info.entity.internal.PageSummary;

import java.util.List;

public interface FaceEntityAssociationClient {

    List<FaceEntityAssociation> associate(String targetImageUrl, PageSummary pageSummary);
}
