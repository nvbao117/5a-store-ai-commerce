package com.example.online_shoe_store.Service.ai.memory;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;

/**
 * Agent để tóm tắt context hội thoại dài
 * Giúp giảm token và tập trung vào thông tin quan trọng
 */
public interface ContextSummarizerAgent {

    @UserMessage("""
        Tóm tắt cuộc hội thoại dưới đây trong 2-3 câu ngắn gọn.
        
        GIỮ LẠI THÔNG TIN QUAN TRỌNG:
        - Tên sản phẩm đã đề cập
        - Size, màu sắc ưa thích của khách
        - Mã đơn hàng (nếu có)
        - Vấn đề chính khách đang gặp
        - Các quyết định đã đưa ra
        
        BỎ QUA:
        - Câu chào hỏi, xã giao
        - Nội dung trùng lặp
        - Chi tiết không liên quan
        
        Hội thoại cần tóm tắt:
        {{it}}
        """)
    @Agent(description = "Tóm tắt hội thoại để giảm context", outputKey = "summary")
    String summarize(String conversation);
}
