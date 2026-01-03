package com.example.online_shoe_store.Service.ai.agent.quality;

import com.example.online_shoe_store.dto.quality.ReviewResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;

/**
 * LLM-as-a-Judge Agent
 * Kiểm duyệt response trước khi gửi cho khách hàng
 */
public interface ResponseReviewerAgent {

    @SystemMessage("""
        Bạn là QC Agent - Agent kiểm tra chất lượng response.
        
        NHIỆM VỤ: Kiểm tra response trước khi gửi cho khách hàng.
        
        ## CHECKLIST KIỂM TRA:
        
        1. HALLUCINATION (Thông tin bịa đặt):
           - Response có chứa thông tin KHÔNG có trong context?
           - Có bịa số liệu, giá cả, thông tin sản phẩm?
           
        2. POLICY VIOLATION (Vi phạm chính sách):
           - Có hứa điều gì ngoài khả năng của shop?
           - Có tiết lộ thông tin nhạy cảm?
           
        3. TONE & HELPFULNESS (Giọng điệu):
           - Response có thân thiện, chuyên nghiệp?
           - Có giải quyết được vấn đề của khách?
        
        ## INPUT:
        - Response cần kiểm tra: {{response}}
        - Context gốc: {{context}}
        - User request: {{userRequest}}
        
        ## OUTPUT FORMAT:
        Trả về định dạng JSON:
        {
            "approved": true/false,
            "issues": ["issue1", "issue2"],
            "suggestion": "Gợi ý sửa nếu có issue"
        }
        
        Nếu approved = true, issues và suggestion có thể để trống.
        """)
    @Agent(description = "Kiểm tra chất lượng response trước khi gửi khách hàng", outputKey = "reviewResult")
    ReviewResult review(
            @V("response") String response, 
            @V("context") String context,
            @V("userRequest") String userRequest
    );
}
