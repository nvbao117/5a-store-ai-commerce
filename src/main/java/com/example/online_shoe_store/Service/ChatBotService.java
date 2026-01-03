package com.example.online_shoe_store.Service;

import com.example.online_shoe_store.Entity.Conversation;
import com.example.online_shoe_store.Entity.ConversationMessage;
import com.example.online_shoe_store.Entity.User;
import com.example.online_shoe_store.Entity.enums.MessageRole;
import com.example.online_shoe_store.Repository.ConversationMessageRepository;
import com.example.online_shoe_store.Repository.ConversationRepository;
import com.example.online_shoe_store.Repository.UserRepository;
import com.example.online_shoe_store.Service.ai.agent.ShopChatAgent;
import com.example.online_shoe_store.Service.ai.context.DirectContextService;
import com.example.online_shoe_store.Service.ai.context.ProductJsonHolder;
import com.example.online_shoe_store.Service.ai.monitoring.AgentFileLogger;
import com.example.online_shoe_store.Service.ai.monitoring.ToolLoggingChatModelListener;
import com.example.online_shoe_store.dto.request.ApiChatRequest;
import com.example.online_shoe_store.dto.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBotService {

    private final ShopChatAgent shopChatAgent;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AgentFileLogger agentFileLogger;
    private final com.example.online_shoe_store.Service.ai.context.DirectContextService directContextService;
    private final ProductJsonHolder productJsonHolder;

    @Transactional
    public ChatResponse processMessage(ApiChatRequest request) {
        long startTime = System.currentTimeMillis();

        String userId = getAuthenticatedUserId();
        User user = "guest".equals(userId) ? null : userRepository.findById(userId).orElse(null);
        
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId() 
                : UUID.randomUUID().toString();

        String userMessage = request.getMessage();

        log.debug("Chat: userId={}, sessionId={}, message={}", userId, sessionId, userMessage);

        // Log vào file
        agentFileLogger.logUserMessage(sessionId, userMessage);
        
        // Set session ID for thread-local correlation with ChatModelListener
        ToolLoggingChatModelListener.setCurrentSession(sessionId);

        // 1.Lấy hoặc tạo hội thoại mới
        Conversation conversation = conversationRepository.findBySessionIdAndIsActiveTrue(sessionId)
                .orElseGet(() -> {
                    Conversation newConv = Conversation.builder()
                            .sessionId(sessionId)
                            .user(user)
                            .build();
                    return conversationRepository.save(newConv);
                });

        // 2. Lưu message
        ConversationMessage userMsg = ConversationMessage.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(userMessage)
                .build();
        messageRepository.save(userMsg);
        conversation.setMessageCount(conversation.getMessageCount() + 1);

        // 3. DIRECT context fetch
        long contextStart = System.currentTimeMillis();
        String context = directContextService.prepareContext(sessionId, userId, userMessage);
        log.info("Context prepared in {}ms", System.currentTimeMillis() - contextStart);

        // 4. Call agent with pre-fetched context
        String answer;
        try {
            answer = shopChatAgent.chat(sessionId, userId, userMessage, context);
            
            // Post-process: Ensure JSON block is in response (inject if LLM forgot)
            answer = productJsonHolder.ensureJsonBlock(sessionId, answer);
        } catch (Exception e) {
            log.error("Agent error: {}", e.getMessage(), e);
            answer = "Xin lỗi, có lỗi xảy ra. Vui lòng thử lại sau.";
        } finally {
            ToolLoggingChatModelListener.clearCurrentSession();
            productJsonHolder.clearSession(sessionId);
        }

        // 4. Save bot response
        long duration = System.currentTimeMillis() - startTime;
        ConversationMessage botMsg = ConversationMessage.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(answer)
                .processingTimeMs((int) duration)
                .build();
        messageRepository.save(botMsg);
        conversation.setMessageCount(conversation.getMessageCount() + 1);

        // Log assistant response to file
        agentFileLogger.logAssistantResponse(sessionId, answer, duration);

        log.info("Chat completed: {}ms, session={}", duration, sessionId);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .response(answer)
                .type("TEXT")
                .build();
    }

    /**
     * Lấy userId (UUID) từ authenticated user, hoặc "guest" nếu chưa đăng nhập
     */
    private String getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "guest";
        }
        
        String username = auth.getName();
        
        // Query database để lấy UUID userId
        return userRepository.findByUsername(username)
                .map(user -> user.getUserId())
                .orElse("guest");
    }


    /**
     * NEW: Streaming chat response
     * NOTE: Currently uses word-by-word fake streaming.
     * Guaranteed to work regardless of library versions.
     */
    public void processMessageStreaming(String message, String sessionId,
                                       org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter,
                                       Object streamingModel) { // Keeping signature to avoid Controller change
        final String finalMessage = message;
        final String initialSessionId = sessionId;
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String userId = getAuthenticatedUserId();
                String currentSessionId = (initialSessionId != null && !initialSessionId.isBlank())
                    ? initialSessionId
                    : UUID.randomUUID().toString();
                
                long contextStart = System.currentTimeMillis();
                String context = directContextService.prepareContext(currentSessionId, userId, finalMessage);
                log.info("Context prepared in {}ms", System.currentTimeMillis() - contextStart);
                
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event()
                    .name("thinking")
                    .data("AI đang suy nghĩ..."));
                
                // Get full response from agent (Synchronous)
                String fullResponse = shopChatAgent.chat(currentSessionId, userId, finalMessage, context);
                
                // Stream word by word for better UX (Fake Streaming)
                String[] words = fullResponse.split(" ");
                for (String word : words) {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                        .event()
                        .name("message")
                        .data(word + " "));
                    Thread.sleep(15); // Fast typing 15ms
                }
                
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event()
                    .name("done")
                    .data(Map.of("sessionId", currentSessionId)));
                
                emitter.complete();
                log.info("Streaming completed for session: {}", currentSessionId);
                
            } catch (Exception e) {
                log.error("Streaming error: {}", e.getMessage(), e);
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                        .event()
                        .name("error")
                        .data("Xin lỗi, có lỗi xảy ra: " + e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });
    }
}
