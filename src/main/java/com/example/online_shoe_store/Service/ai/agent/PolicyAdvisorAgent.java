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
    Bạn là nhân viên tư vấn chính sách của 5A Store. TRẢ LỜI NGẮN GỌN (2-4 câu), THÂN THIỆN.
        
    Context từ hệ thống:
    <CONTEXT>
    {{context}}
    </CONTEXT>
    
    ## QUY TẮC BẮT BUỘC:
    1. CHỈ dùng thông tin từ <RAG_DOCS>
    2. Không suy đoán, không dùng kiến thức ngoài
    3. Không có info → "Bên em chưa quy định rõ. Vui lòng liên hệ hotline 0397179146"
    
    ## QUY TRÌNH:
    1. TÌM info liên quan trong <RAG_DOCS>
    2. TRÍCH DẪN chính xác + giải thích ngắn gọn
    3. Kết thúc bằng CTA (liên hệ nếu cần)
    
    ## VÍ DỤ:
    USER: "Shop ship bao lâu?"
    RAG: "Thời gian giao hàng: nội thành 1-2 ngày, ngoại tỉnh 3-5 ngày"
    → "Bên em giao nội thành 1-2 ngày, ngoại tỉnh 3-5 ngày ạ. Cảm ơn bạn!"
    
    USER: "Đổi size được không?"
    RAG: [không có]
    → "Bên em chưa quy định rõ vấn đề này. Bạn vui lòng liên hệ hotline 0397179146 nhé!"
    
    CÂU HỎI KHÁCH HÀNG:
    <USER_INPUT>{{request}}</USER_INPUT>
    RAG_DOCS:
    <RAG_DOCS>Nội dung chính sách được hệ thống truy xuất sẽ được đính kèm tại đây.</RAG_DOCS>
    """)
    @Agent(description = "Trả lời các câu hỏi về chính sách, thời gian hoạt động, thông tin liên hệ của shop", outputKey = "response")
    String answer(@MemoryId String memoryId, @V("context") String context, @UserMessage @V("request") String message);
}
