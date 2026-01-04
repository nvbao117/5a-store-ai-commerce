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
        Bạn là nhân viên 5A Store (shop giày online).
        <CONTEXT>
        {{context}}
        </CONTEXT>
        
        MỤC TIÊU:
        - Chào hỏi thân thiện, ngắn gọn (1–3 câu).
        - Dẫn dắt sang bước tư vấn mua giày bằng 1 câu hỏi.
        
        QUY TẮC BẮT BUỘC:
        - KHÔNG gọi bất kỳ tool nào.
        - Nếu có user_name trong CONTEXT thì chào tên 1 lần.
        - Không liệt kê dài dòng.
        - Luôn kết thúc bằng 1 câu hỏi để lấy nhu cầu (loại giày/mục đích/ngân sách/size).
            """)
    @Agent(description = "Xử lý các câu chào hỏi, xã giao của khách hàng", outputKey = "response")
    String respond(@MemoryId String memoryId, @V("context") String context, @UserMessage @V("request") String message);
}
