package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Sophnet 视觉大模型人物搜索配置。
 */
@Getter
@Setter
public class SophnetVisionApiProperties {

    private boolean enabled;
    private String baseUrl = "https://www.sophnet.com/api/open-apis/v1/chat/completions";
    private String apiKey;
    private List<String> models = new ArrayList<>(List.of(
            "gemini-3.1-pro-preview"
    ));
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 75000;
    private int maxRetries = 2;
    private long backoffInitialMs = 500L;
    private int maxEvidenceUrls = 8;
    private String systemPrompt = """
            You are a public-person profile inference assistant. Use the image only as one signal, identify only public figures when evidence is sufficient, and ground the answer in public sources. Return strict JSON only.
            """;
    private String userPrompt = """
            Identify the most likely public person in the image and infer a structured public profile for comparison testing.
            Ask in English for: person's name, public social media accounts (X/Twitter, Facebook, Instagram, LinkedIn and other verified public profiles), employer/company, current or notable job title, and concise public biography.
            Return strict JSON only, without Markdown, apology, reasoning, or extra text:
            {
              "candidateName": "Most likely person's name, or empty string if not enough evidence",
              "confidence": 0.0,
              "summary": "Chinese structured summary. Mention uncertainty when evidence is weak.",
              "company": "Employer or organization, or empty string",
              "position": "Job title or public role, or empty string",
              "socialAccounts": [
                {"platform": "X", "username": "handle", "url": "https://...", "confidence": "confirmed|suspected"}
              ],
              "evidenceUrls": ["Public source URL supporting the inference"],
              "tags": ["public role or occupation"],
              "sourceNotes": ["Short note describing which public sources/citations were used"]
            }
            Requirements:
            1. Do not identify a private or non-public person from the image.
            2. Do not invent URLs, employers, positions, or social accounts.
            3. If sources are insufficient, keep uncertain fields empty and explain the uncertainty in Chinese summary.
            4. The summary must be in Chinese; field names remain English.
            """;
}
