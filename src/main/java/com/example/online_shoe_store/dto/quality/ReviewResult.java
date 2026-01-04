package com.example.online_shoe_store.dto.quality;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/**
 * Result of response quality review by ResponseReviewerAgent
 * Dùng @Description để LangChain4j có thể tự động parse structured output
 */
public record ReviewResult(
    @Description("true nếu response đạt chất lượng, false nếu có vấn đề")
    boolean approved,
    
    @Description("Danh sách các vấn đề phát hiện (hallucination, policy violation, tone issues). Để trống nếu approved=true")
    List<String> issues,
    
    @Description("Gợi ý cách sửa response nếu có vấn đề. Để trống nếu approved=true")
    String suggestion
) {
    public static ReviewResult createApproved() {
        return new ReviewResult(true, List.of(), null);
    }
    
    public static ReviewResult createRejected(List<String> issues, String suggestion) {
        return new ReviewResult(false, issues, suggestion);
    }
}
