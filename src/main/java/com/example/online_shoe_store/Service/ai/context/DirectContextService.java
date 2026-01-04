package com.example.online_shoe_store.Service.ai.context;

import com.example.online_shoe_store.Entity.Conversation;
import com.example.online_shoe_store.Entity.ConversationMessage;
import com.example.online_shoe_store.Entity.User;
import com.example.online_shoe_store.Repository.ConversationMessageRepository;
import com.example.online_shoe_store.Repository.ConversationRepository;
import com.example.online_shoe_store.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class DirectContextService {

    private final ProductJsonHolder productJsonHolder;
    private final ConversationMessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    
    private static final int RECENT_MESSAGES_TO_KEEP = 4;

    public String prepareContext(String sessionId, String userId, String userMessage) {
        StringBuilder context = new StringBuilder();

        
        context.append(buildStaticContext(userId, sessionId));

        //Product context (từ tool calls trước đó)
        String productJson = productJsonHolder.getJsonForSession(sessionId);
        if (productJson != null && !productJson.isEmpty()) {
            context.append("<PRODUCTS>\n")
                   .append(productJson)
                   .append("\n</PRODUCTS>\n\n");
        }
        
        //Conversation history (summary + recent từ DB)
        String history = getDynamicConversationHistory(sessionId);
        if (history != null && !history.isEmpty()) {
            context.append("<HISTORY>\n")
                   .append(history)
                   .append("</HISTORY>\n\n");
        }
        return context.toString();
    }

    //Thông tin quan trọng luôn cần
    private String buildStaticContext(String userId, String sessionId) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("<STATIC>\n");
        
        String userName = getUserDisplayName(userId);
        sb.append("user_id: ").append(userId != null ? userId : "guest").append("\n");
        if (userName != null) {
            sb.append("user_name: ").append(userName).append("\n");
        }
        sb.append("</STATIC>\n\n");
        
        return sb.toString();
    }

    /**
     * DYNAMIC: Fetch conversation history từ DB
     */
    private String getDynamicConversationHistory(String sessionId) {
        try {
            var conversationOpt = conversationRepository.findBySessionIdAndIsActiveTrue(sessionId);
            if (conversationOpt.isEmpty()) {
                return null;
            }
            
            Conversation conversation = conversationOpt.get();
            StringBuilder result = new StringBuilder();
            
            // Pre-computed summary (nếu có)
            String summary = conversation.getSummary();
            if (summary != null && !summary.isBlank()) {
                result.append("[SUMMARY]\n")
                      .append(summary)
                      .append("\n\n");
            }
            
            // Recent messages
            List<ConversationMessage> recentMessages = messageRepository
                    .findRecentBySessionId(sessionId, RECENT_MESSAGES_TO_KEEP);
            
            if (recentMessages != null && !recentMessages.isEmpty()) {
                java.util.Collections.reverse(recentMessages);
                
                result.append("[RECENT]\n");
                for (ConversationMessage msg : recentMessages) {
                    result.append(msg.getRole()).append(": ")
                          .append(msg.getContent()).append("\n");
                }
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("[DirectContextService] Error: {}", e.getMessage());
            return null;
        }
    }

    private String getUserDisplayName(String userId) {
        if (userId == null || userId.isBlank() || "guest".equals(userId)) {
            return null;
        }
        try {
            return userRepository.findById(userId)
                    .map(User::getName)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}