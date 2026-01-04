package com.example.online_shoe_store.Service.ai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class VectorStoreConfig {
    @Value("${chroma.base.url}")
    private String chromaBaseUrl;

    @Bean("productEmbeddingStore")
    public EmbeddingStore<TextSegment> productEmbeddingStore() {
        log.info("Initializing product embedding store...");
        return ChromaEmbeddingStore.builder()
                .baseUrl(chromaBaseUrl)
                .collectionName("product-embeddings")
                .build();
    }

    @Bean("faqEmbeddingStore")
    public EmbeddingStore<TextSegment> faqEmbeddingStore() {
        log.info("Initializing FAQ embedding store...");
        return ChromaEmbeddingStore.builder()
                .baseUrl(chromaBaseUrl)
                .collectionName("faq_shoe_store")
                .build();
    }
}
