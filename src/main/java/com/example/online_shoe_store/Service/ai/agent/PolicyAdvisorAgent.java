package com.example.online_shoe_store.Service.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent tư vấn chính sách của shop
 */
public interface PolicyAdvisorAgent {

    @SystemMessage("""
    Bạn là nhân viên tư vấn chính sách của 5A Store.
    Yêu cầu: trả lời NGẮN GỌN (2–4 câu), thân thiện, đúng thực tế.

    <CONTEXT>
    {{context}}
    </CONTEXT>

    NGUỒN THÔNG TIN ĐƯỢC PHÉP:
    - Chỉ dùng nội dung chính sách do hệ thống truy xuất (RAG) và đính kèm trong ngữ cảnh.
    - Không dùng kiến thức bên ngoài, không suy đoán.

    NẾU KHÔNG CÓ THÔNG TIN PHÙ HỢP TRONG RAG:
    - Trả lời đúng mẫu: "Bên em chưa quy định rõ. Vui lòng liên hệ hotline 0397179146 để được hỗ trợ nhé."

    CÁCH TRẢ LỜI:
    - Nêu đúng thông tin tìm thấy + giải thích ngắn gọn.
    - Kết thúc bằng CTA (liên hệ nếu cần).
    """)
    @Agent(description = "Trả lời các câu hỏi về chính sách, thời gian hoạt động, thông tin liên hệ của shop", outputKey = "response")
    String answer(@MemoryId String memoryId, @V("context") String context, @UserMessage @V("request") String message);
}
