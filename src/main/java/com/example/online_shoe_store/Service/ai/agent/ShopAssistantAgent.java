package com.example.online_shoe_store.Service.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Main orchestrator agent cho shop chatbot
 * Điều phối các sub-agents để xử lý yêu cầu khách hàng
 */
public interface ShopAssistantAgent {
    @Agent
    String chat(@MemoryId @V("sessionId") String sessionId, 
                @V("userId") String userId, 
                @UserMessage @V("request") String request,
                @V("context") String context);
}
