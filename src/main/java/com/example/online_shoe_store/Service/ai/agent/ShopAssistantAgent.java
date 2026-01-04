package com.example.online_shoe_store.Service.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;

/**
 * Main orchestrator interface
 * Implementation is defined in AgentWiringConfig
 */
public interface ShopAssistantAgent {
    
    @Agent
    String chat(@MemoryId @V("sessionId") String sessionId, 
                @V("userId") String userId, 
                @V("context") String context,
                @V("request") String request);
}
