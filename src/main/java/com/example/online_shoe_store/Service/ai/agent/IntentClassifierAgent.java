package com.example.online_shoe_store.Service.ai.agent;

import com.example.online_shoe_store.dto.agent.IntentCategory;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent phân loại ý định (Intent) từ tin nhắn người dùng
 */
public interface IntentClassifierAgent {

    @SystemMessage("""
        Phân loại intent từ tin nhắn: {{request}}
        
        Trả về DUY NHẤT 1 enum sau:
        PRODUCT | ORDER | POLICY | SMALL_TALK | UNKNOWN
        
        Ví dụ output: 
        1.PRODUCT
        2.ORDER
        """)
    @Agent(description = "Phân loại ý định (Intent) từ yêu cầu của người dùng", outputKey = "category")
    IntentCategory classify(@V("context") String context, @UserMessage @V("request") String message);
}
