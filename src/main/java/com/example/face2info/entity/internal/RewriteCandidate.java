package com.example.face2info.entity.internal;

/**
 * 查询改写候选项。
 */
public class RewriteCandidate {

    private String rewrittenQuery;
    private String rewriteReason;
    private double safetyScore;
    private double semanticPreservationScore;
    private double finalScore;

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public RewriteCandidate setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
        return this;
    }

    public String getRewriteReason() {
        return rewriteReason;
    }

    public RewriteCandidate setRewriteReason(String rewriteReason) {
        this.rewriteReason = rewriteReason;
        return this;
    }

    public double getSafetyScore() {
        return safetyScore;
    }

    public RewriteCandidate setSafetyScore(double safetyScore) {
        this.safetyScore = safetyScore;
        return this;
    }

    public double getSemanticPreservationScore() {
        return semanticPreservationScore;
    }

    public RewriteCandidate setSemanticPreservationScore(double semanticPreservationScore) {
        this.semanticPreservationScore = semanticPreservationScore;
        return this;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public RewriteCandidate setFinalScore(double finalScore) {
        this.finalScore = finalScore;
        return this;
    }
}
