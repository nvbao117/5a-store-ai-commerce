package com.example.online_shoe_store.Service.ai.config;

import com.example.online_shoe_store.Service.ai.agent.*;
import com.example.online_shoe_store.Service.ai.monitoring.EventLoggingAgentListener;
import com.example.online_shoe_store.Service.ai.tool.*;
import com.example.online_shoe_store.dto.agent.IntentCategory;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
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

import java.util.Objects;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class AgentWiringConfig {

    private final EventLoggingAgentListener eventLoggingAgentListener;

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return id -> MessageWindowChatMemory.withMaxMessages(10);
    }

    // ====== RAG cho policy (nếu cần) ======

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

    // ====== SHOP AGENTIC SYSTEM ======

    @Bean
    public IntentRouter intentRouter(
            @Qualifier("workerModel") ChatModel baseModel
    ){
        return AgenticServices.agentBuilder(IntentRouter.class)
                .chatModel(baseModel)
                .outputKey("category")
                .build();
    }

    @Bean
    public ProductExpertAgent productExpertAgent(
                @Qualifier("workerModel") ChatModel baseModel,
                ProductSearchTools productSearchTools,
                InventoryTools inventoryTools,
                CartTools cartTools
        ) {
        return AgenticServices.agentBuilder(ProductExpertAgent.class)
                .chatModel(baseModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(8))
                .tools(productSearchTools, inventoryTools, cartTools)
                .outputKey("response")
                .listener(eventLoggingAgentListener)
                .build();
    }

    @Bean
    public OrderExpertAgent orderExpertAgent(
            @Qualifier("workerModel") ChatModel baseModel,
            OrderTools orderTools
    ) {
        return AgenticServices.agentBuilder(OrderExpertAgent.class)
                .chatModel(baseModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(8))
                .tools(orderTools)
                .outputKey("response")
                .listener(eventLoggingAgentListener)
                .build();
    }
    @Bean
    public PolicyExpertAgent policyExpertAgent(
            @Qualifier("workerModel") ChatModel baseModel,
            ContentRetriever policyRetriever
    ) {
        return AgenticServices.agentBuilder(PolicyExpertAgent.class)
                .chatModel(baseModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(4))
                .contentRetriever(policyRetriever)
                .outputKey("response")
                .listener(eventLoggingAgentListener)
                .build();
    }

    @Bean
    public SmallTalkAgent smallTalkAgent(
            @Qualifier("workerModel") ChatModel baseModel
    ) {
        return AgenticServices.agentBuilder(SmallTalkAgent.class)
                .chatModel(baseModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(4))
                .outputKey("response")
                .listener(eventLoggingAgentListener)
                .build();
    }

    @Bean
    public ShopChatAgent shopChatAgent(
            IntentRouter intentRouter,
            ProductExpertAgent productExpertAgent,
            OrderExpertAgent orderExpertAgent,
            PolicyExpertAgent policyExpertAgent,
            SmallTalkAgent smallTalkAgent
    ) {
        UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
                .subAgents(
                        scope -> Objects.equals(scope.readState("category"), IntentCategory.PRODUCT),
                        productExpertAgent
                )
                .subAgents(
                        scope -> Objects.equals(scope.readState("category"), IntentCategory.ORDER),
                        orderExpertAgent
                )
                .subAgents(
                        scope -> Objects.equals(scope.readState("category"), IntentCategory.POLICY),
                        policyExpertAgent
                )
                .subAgents(
                        scope -> Objects.equals(scope.readState("category"), IntentCategory.SMALL_TALK),
                        smallTalkAgent
                )
                .subAgents(
                        scope -> scope.readState("category", IntentCategory.UNKNOWN) == IntentCategory.UNKNOWN,
                        policyExpertAgent
                )
                .build();

        // Context is now pre-fetched in ChatBotService - no longer need contextManagerAgent here
        return AgenticServices.sequenceBuilder(ShopChatAgent.class)
                .subAgents(intentRouter, expertsAgent)
                .listener(eventLoggingAgentListener)
                .outputKey("response")
                .build();
    }
}
