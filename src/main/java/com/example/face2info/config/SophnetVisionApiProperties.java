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
    private String baseUrl;
    private String apiKey;
    private List<String> models = new ArrayList<>(List.of(
            "gemini-3.1-pro-preview",
            "gpt-5.5",
            "claude-opus-4-7"
    ));
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 75000;
    private int maxRetries = 2;
    private long backoffInitialMs = 500L;
    private int maxEvidenceUrls = 8;
    private String systemPrompt = """
            You are a Visual Ground Truth extractor. Use the original image as a reference object for element comparison, not as an identity source. Return strict JSON only.
            """;
    private String userPrompt = """
            Build a Visual Ground Truth reference from the original image for later comparison with candidate profiles.
            Ignore the person's name predicted from the image. Only fill candidateName when the face is clearly a widely known head of state, senior public politician, or celebrity; otherwise candidateName must be an empty string.
            Extract hard visual fingerprints that can be checked against other evidence. Do not search the web and do not infer employer, position, social accounts, biography, or URLs from appearance.
            Return strict JSON only, without Markdown, apology, reasoning, or extra text:
            {
              "candidateName": "",
              "confidence": 0.0,
              "summary": "Chinese one-sentence note that this module is only a visual reference for element comparison.",
              "company": "",
              "position": "",
              "socialAccounts": [],
              "visualGroundTruth": {
                "ageRange": "estimated age range, or unknown",
                "skinToneOrEthnicity": "visible skin tone / broad ethnicity cue, or unknown",
                "hairStyleAndColor": "hair style and hair color, or unknown",
                "eyewear": "glasses / sunglasses / none / unknown",
                "clothingStyle": "formal / sportswear / uniform / workwear / casual / unknown",
                "environmentClues": "office / outdoor / vehicle / stage / landmark / unknown",
                "visibleTextLogoBadge": "visible text, logo, badge, work card, or none visible"
              },
              "evidenceUrls": [],
              "tags": ["visual_ground_truth"],
              "sourceNotes": ["Only visual facts visible in the uploaded image are used."]
            }
            Requirements:
            1. Treat this module as a comparison reference, not an identity judgment.
            2. Do not identify private or non-public people.
            3. Do not invent names, organizations, roles, social accounts, URLs, logos, or badges.
            4. Mark uncertain or invisible elements as "unknown" or "none visible".
            5. The summary must be in Chinese; field names remain English.
            """;
}
