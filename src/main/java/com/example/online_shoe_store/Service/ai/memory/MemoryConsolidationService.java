package com.example.online_shoe_store.Service.ai.memory;

import com.example.online_shoe_store.Entity.UserProfileMemory;
import com.example.online_shoe_store.Repository.UserProfileMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Post-turn Memory Consolidation Service
 * Chạy async sau mỗi turn để extract preference changes và update long-term memory
 * Giải quyết vấn đề conflict resolution khi user thay đổi preferences
 */
@Service
@Slf4j
public class MemoryConsolidationService {

    private final UserProfileMemoryRepository profileRepository;
    private final ChatModel extractorModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MemoryConsolidationService(
            UserProfileMemoryRepository profileRepository,
            @Qualifier("workerModel") ChatModel extractorModel) {
        this.profileRepository = profileRepository;
        this.extractorModel = extractorModel;
    }

    /**
     * Extract preference updates from conversation and consolidate into long-term memory
     * Chạy async sau mỗi turn
     */
    @Async
    @Transactional
    public void consolidateMemory(String userId, String userMessage, String assistantResponse) {
        if (userId == null || userId.isBlank() || "guest".equals(userId)) {
            return;
        }

        try {
            log.debug("[MemoryConsolidation] Checking for preference updates for user: {}", userId);
            
            // Extract potential preference updates from the conversation
            String extractionPrompt = buildExtractionPrompt(userMessage, assistantResponse);
            String extractedJson = extractorModel.chat(extractionPrompt);
            
            if (extractedJson == null || extractedJson.isBlank() || 
                extractedJson.contains("NO_UPDATES") || extractedJson.contains("null")) {
                log.debug("[MemoryConsolidation] No preference updates detected");
                return;
            }
            
            // Parse and apply updates
            applyPreferenceUpdates(userId, extractedJson);
            
        } catch (Exception e) {
            log.warn("[MemoryConsolidation] Error: {}", e.getMessage());
            // Fail silently - không ảnh hưởng user experience
        }
    }

    private String buildExtractionPrompt(String userMessage, String assistantResponse) {
        return """
            Analyze this conversation turn and extract any NEW user preferences mentioned.
            Look for: preferred shoe size, favorite brand, interests, shipping preference, etc.
            
            USER: %s
            ASSISTANT: %s
            
            ONLY extract if user explicitly mentions preferences. Examples:
            - "Tôi thường mua size 42" → {"preferred_size": "42"}
            - "Mình thích Nike hơn" → {"preferred_brand": "Nike"}
            - "Tôi hay chạy bộ" → {"interests": ["chạy bộ"]}
            
            If NO clear preferences mentioned, respond with: NO_UPDATES
            
            If preferences found, respond with ONLY valid JSON (no markdown):
            """.formatted(userMessage, assistantResponse);
    }

    @Transactional
    protected void applyPreferenceUpdates(String userId, String extractedJson) {
        try {
            // Clean JSON if wrapped in markdown
            String cleanJson = extractedJson.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json?\\n?", "").replaceAll("```", "").trim();
            }
            
            if (cleanJson.isBlank() || cleanJson.equals("{}")) {
                return;
            }
            
            ObjectNode updates = (ObjectNode) objectMapper.readTree(cleanJson);
            if (updates.isEmpty()) {
                return;
            }
            
            // Get or create user profile
            UserProfileMemory profile = profileRepository.findByUserId(userId)
                    .orElseGet(() -> UserProfileMemory.builder()
                            .userId(userId)
                            .profileJson("{}")
                            .conversationCount(0)
                            .orderCount(0)
                            .build());
            
            // Merge updates into existing profile
            ObjectNode existingProfile = parseProfileJson(profile.getProfileJson());
            
            updates.fieldNames().forEachRemaining(field -> {
                existingProfile.set(field, updates.get(field));
                log.info("[MemoryConsolidation] Updated preference for user {}: {} = {}", 
                        userId, field, updates.get(field));
            });
            
            profile.setProfileJson(objectMapper.writeValueAsString(existingProfile));
            profile.setLastActiveAt(LocalDateTime.now());
            profileRepository.save(profile);
            
        } catch (Exception e) {
            log.warn("[MemoryConsolidation] Failed to apply updates: {}", e.getMessage());
        }
    }

    private ObjectNode parseProfileJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return (ObjectNode) objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Increment conversation count for user
     */
    @Async
    @Transactional
    public void incrementConversationCount(String userId) {
        if (userId == null || userId.isBlank() || "guest".equals(userId)) {
            return;
        }
        profileRepository.incrementConversationCount(userId);
    }
}
