package com.example.online_shoe_store.dto.quality;

import java.util.List;

/**
 * Result of response quality review by ResponseReviewerAgent
 */
public record ReviewResult(
    boolean approved,
    List<String> issues,
    String suggestion
) {
    public static ReviewResult createApproved() {
        return new ReviewResult(true, List.of(), null);
    }
    
    public static ReviewResult createRejected(List<String> issues, String suggestion) {
        return new ReviewResult(false, issues, suggestion);
    }
}
