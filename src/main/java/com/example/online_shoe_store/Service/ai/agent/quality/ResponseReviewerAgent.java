package com.example.online_shoe_store.Service.ai.agent.quality;

import com.example.online_shoe_store.dto.quality.ReviewResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LLM-as-a-Judge Agent
 * Kiểm duyệt response trước khi gửi cho khách hàng
 * Sử dụng structured output với ReviewResult record
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
        
        ## QUY TẮC OUTPUT:
        - Nếu response OK -> approved=true, issues=[], suggestion=""
        - Nếu có vấn đề -> approved=false, liệt kê issues và đưa gợi ý sửa
        """)
    @UserMessage("""
        Kiểm tra response sau đây:
        
        [RESPONSE CẦN KIỂM TRA]
        {{response}}
        
        [CONTEXT GỐC]
        {{context}}
        
        [YÊU CẦU CỦA USER]
        {{userRequest}}
        """)
    ReviewResult review(
            @V("response") String response, 
            @V("context") String context,
            @V("userRequest") String userRequest
    );
}
