package com.example.face2info.client.impl;

import com.example.face2info.client.FaceEntityAssociationClient;
import com.example.face2info.entity.internal.FaceEntityAssociation;
import com.example.face2info.entity.internal.NamedEntity;
import com.example.face2info.entity.internal.PageSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@ConditionalOnMissingBean(FaceEntityAssociationClient.class)
public class NoopFaceEntityAssociationClient implements FaceEntityAssociationClient {

    @Override
    public List<FaceEntityAssociation> associate(String targetImageUrl, PageSummary pageSummary) {
        if (pageSummary == null || pageSummary.getNamedEntities() == null || !StringUtils.hasText(targetImageUrl)) {
            return List.of();
        }
        return pageSummary.getNamedEntities().stream()
                .filter(entity -> entity != null && StringUtils.hasText(entity.getText()))
                .map(this::toWeakAssociation)
                .toList();
    }

    private FaceEntityAssociation toWeakAssociation(NamedEntity entity) {
        double baseScore = switch (String.valueOf(entity.getType()).toUpperCase(java.util.Locale.ROOT)) {
            case "PERSON" -> 72.0D;
            case "OCCUPATION" -> 68.0D;
            case "ORG" -> 65.0D;
            default -> 60.0D;
        };
        return new FaceEntityAssociation()
                .setEntityText(entity.getText())
                .setEntityType(entity.getType())
                .setConfidenceScore(Math.min(78.0D, baseScore + Math.max(0, entity.getMentions())))
                .setReason("fallback_entity_context")
                .setSourceUrl(entity.getSourceUrl());
    }
}
