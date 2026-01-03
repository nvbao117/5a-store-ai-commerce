package com.example.online_shoe_store.Service.ai.config;

import com.example.online_shoe_store.Service.ai.agent.*;
import com.example.online_shoe_store.Service.ai.agent.quality.ResponseReviewerAgent;
import com.example.online_shoe_store.Service.ai.memory.ContextSummarizerAgent;
import com.example.online_shoe_store.Service.ai.monitoring.EventLoggingAgentListener;
import com.example.online_shoe_store.Service.ai.tool.*;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class AgentWiringConfig {

    private final EventLoggingAgentListener eventLoggingAgentListener;

    // ====== RAG cho policy ======

    @Bean
    public ContentRetriever policyRetriever(
            @Qualifier("faqEmbeddingStore") EmbeddingStore<TextSegment> store,
            EmbeddingModel embeddingModel
    ) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.7)
                .build();
    }

    // ====== EXPERT AGENTS ======

    @Bean
    public ProductConsultantAgent productConsultantAgent(
                @Qualifier("workerModel") ChatModel baseModel,
                ProductSearchTools productSearchTools,
                InventoryTools inventoryTools,
                CartTools cartTools
        ) {
        return AgenticServices.agentBuilder(ProductConsultantAgent.class)
                .chatModel(baseModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(8))
                .tools(productSearchTools, inventoryTools, cartTools)
                .outputKey("response")
                .listener(eventLoggingAgentListener)
                .build();
    }

    @Bean
    public OrderServiceAgent orderServiceAgent(
            @Qualifier("workerModel") ChatModel baseModel,
            OrderTools orderTools
    ) {
        return AgenticServices.agentBuilder(OrderServiceAgent.class)
                .chatModel(baseModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(8))
                .tools(orderTools)
                .outputKey("response")
                .listener(eventLoggingAgentListener)
                .build();
    }
    
    @Bean
    public PolicyAdvisorAgent policyAdvisorAgent(
            @Qualifier("workerModel") ChatModel baseModel,
            ContentRetriever policyRetriever
    ) {
        return AgenticServices.agentBuilder(PolicyAdvisorAgent.class)
                .chatModel(baseModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(4))
                .contentRetriever(policyRetriever)
                .outputKey("response")
                .listener(eventLoggingAgentListener)
                .build();
    }

    @Bean
    public GreetingAgent greetingAgent(
            @Qualifier("workerModel") ChatModel baseModel
    ) {
        return AgenticServices.agentBuilder(GreetingAgent.class)
                .chatModel(baseModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(4))
                .outputKey("response")
                .listener(eventLoggingAgentListener)
                .build();
    }

    // ====== SUPERVISOR ORCHESTRATOR ======
    
    @Bean
    public SupervisorAgent shopSupervisorAgent(
            @Qualifier("workerModel") ChatModel supervisorModel,
            ProductConsultantAgent productConsultantAgent,
            OrderServiceAgent orderServiceAgent,
            PolicyAdvisorAgent policyAdvisorAgent,
            GreetingAgent greetingAgent
    ) {
        return AgenticServices.supervisorBuilder()
                .chatModel(supervisorModel)
                .subAgents(
                    productConsultantAgent,
                    orderServiceAgent,
                    policyAdvisorAgent,
                    greetingAgent
                )
                .supervisorContext("""
                    OUTPUT FORMAT: MUST be valid JSON ONLY.
                    
                    ROUTING RULES:
                    1. Greetings -> GreetingAgent
                    2. Product -> ProductConsultantAgent
                    3. Order -> OrderServiceAgent
                    4. Policy -> PolicyAdvisorAgent
                    """)
                .maxAgentsInvocations(2)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY)
                .build();
    }

    @Bean
    public ShopAssistantAgent shopAssistantAgent(
            SupervisorAgent shopSupervisorAgent
    ) {
        return (sessionId, userId, request, context) -> {
            String enrichedRequest = String.format("""
                [CONTEXT]
                SessionId: %s
                UserId: %s
                Context: %s
                [/CONTEXT]
                
                User Request: %s
                """, 
                sessionId, 
                userId != null ? userId : "guest",
                context != null ? context : "",
                request
            );
            
            String result = shopSupervisorAgent.invoke(enrichedRequest);
            return result != null ? result : "Xin lỗi, có lỗi xảy ra.";
        };
    }
    
    // ====== QUALITY AGENTS ======
    
    @Bean
    public ResponseReviewerAgent responseReviewerAgent(
            @Qualifier("workerModel") ChatModel baseModel
    ) {
        return AgenticServices.agentBuilder(ResponseReviewerAgent.class)
                .chatModel(baseModel)
                .outputKey("reviewResult")
                .build();
    }
    
    @Bean
    public ContextSummarizerAgent contextSummarizerAgent(
            @Qualifier("workerModel") ChatModel baseModel
    ) {
        return AgenticServices.agentBuilder(ContextSummarizerAgent.class)
                .chatModel(baseModel)
                .build();
    }
    
    // ====== LEGACY BEANS (Forward Compatibility) ======
    

}
