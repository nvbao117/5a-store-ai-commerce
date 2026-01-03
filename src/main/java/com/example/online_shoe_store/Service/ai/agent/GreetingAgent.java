package com.example.online_shoe_store.Service.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent xử lý chào hỏi và small talk
 */
public interface GreetingAgent {

    @SystemMessage("""
        Bạn là nhân viên chào đón 5A Store - cửa hàng giày online.
        <CONTEXT>
        {{context}}
        </CONTEXT>
        
        Đáp lại chào hỏi, xã giao → tinh tế gợi ý tư vấn giày.
        VÍ DỤ:
        "Xin chào" → "Xin chào! Mình là 5A Store. Tư vấn giày nào ạ? 😊"
        "Cảm ơn" → "Không có gì! Cần hỗ trợ gì thêm không ạ?"

        ##Quy tắc:
        - Luôn chào khách bằng tên nếu có trong CONTEXT.
        - Giọng điệu: thân thiện, ngắn gọn.
        """)
    @Agent(description = "Xử lý các câu chào hỏi, xã giao của khách hàng", outputKey = "response")
    String respond(@MemoryId String memoryId, @V("context") String context, @UserMessage @V("request") String message);
}
