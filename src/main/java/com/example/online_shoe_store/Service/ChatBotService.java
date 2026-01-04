package com.example.online_shoe_store.Service;

import com.example.online_shoe_store.Entity.Conversation;
import com.example.online_shoe_store.Entity.ConversationMessage;
import com.example.online_shoe_store.Entity.User;
import com.example.online_shoe_store.Entity.enums.MessageRole;
import com.example.online_shoe_store.Repository.ConversationMessageRepository;
import com.example.online_shoe_store.Repository.ConversationRepository;
import com.example.online_shoe_store.Repository.UserRepository;
import com.example.online_shoe_store.Service.ai.agent.ShopAssistantAgent;
import com.example.online_shoe_store.Service.ai.agent.quality.ResponseReviewerAgent;
import com.example.online_shoe_store.Service.ai.context.DirectContextService;
import com.example.online_shoe_store.Service.ai.context.ProductJsonHolder;
import com.example.online_shoe_store.Service.ai.memory.AsyncSummarizationService;
import com.example.online_shoe_store.Service.ai.memory.MemoryConsolidationService;
import com.example.online_shoe_store.Service.ai.monitoring.AgentFileLogger;
import com.example.online_shoe_store.Service.ai.monitoring.ToolLoggingChatModelListener;
import com.example.online_shoe_store.dto.quality.ReviewResult;
import com.example.online_shoe_store.dto.request.ApiChatRequest;
import com.example.online_shoe_store.dto.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ChatBotService - Xử lý tin nhắn chat với AI
 * 
 * Transaction scope tách biệt:
 * - saveUserMessage(): @Transactional
 * - AI Agent calls: KHÔNG transaction
 * - saveBotMessage(): @Transactional
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBotService {

    private final ShopAssistantAgent shopAssistantAgent;
    private final ResponseReviewerAgent responseReviewerAgent;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AgentFileLogger agentFileLogger;
    private final DirectContextService directContextService;
    private final ProductJsonHolder productJsonHolder;
    private final AsyncSummarizationService asyncSummarizationService;
    private final MemoryConsolidationService memoryConsolidationService;

    @Value("${ai.response-review.enabled:false}")
    private boolean responseReviewEnabled;

    public ChatResponse processMessage(ApiChatRequest request) {
        long startTime = System.currentTimeMillis();

        String userId = getAuthenticatedUserId();
        User user = "guest".equals(userId) ? null : userRepository.findById(userId).orElse(null);

        String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        String userMessage = request.getMessage();

        log.debug("Chat: userId={}, sessionId={}, message={}", userId, sessionId, userMessage);
        agentFileLogger.logUserMessage(sessionId, userMessage);
        ToolLoggingChatModelListener.setCurrentSession(sessionId);

        // 1. Lưu message user (TRANSACTION 1)
        Conversation conversation = saveUserMessage(sessionId, user, userMessage);

        // 2. Chuẩn bị context (NO TRANSACTION - chỉ đọc)
        String context = directContextService.prepareContext(sessionId, userId, userMessage);

        // 3. Gọi AI Agent (NO TRANSACTION - external call)
        String answer;
        try {
            answer = generateResponse(sessionId, userId, userMessage, context);
            answer = productJsonHolder.ensureJsonBlock(sessionId, answer);
        } catch (Exception e) {
            log.error("Agent error: {}", e.getMessage(), e);
            answer = "Xin lỗi, có lỗi xảy ra. Vui lòng thử lại sau.";
        } finally {
            ToolLoggingChatModelListener.clearCurrentSession();
            productJsonHolder.clearSession(sessionId);
        }

        // 4. Lưu response bot
        long duration = System.currentTimeMillis() - startTime;
        saveBotMessage(conversation, answer, duration);

        agentFileLogger.logAssistantResponse(sessionId, answer, duration);

        // 5. Background tasks (async)
        asyncSummarizationService.triggerSummarizationIfNeeded(sessionId);
        memoryConsolidationService.consolidateMemory(userId, userMessage, answer);

        log.info("Chat completed: {}ms, session={}", duration, sessionId);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .response(answer)
                .type("TEXT")
                .build();
    }

    /**
     Lưu message user
     */
    @Transactional
    public Conversation saveUserMessage(String sessionId, User user, String userMessage) {
        Conversation conversation = conversationRepository.findBySessionIdAndIsActiveTrue(sessionId)
                .orElseGet(() -> {
                    Conversation newConv = Conversation.builder()
                            .sessionId(sessionId)
                            .user(user)
                            .build();
                    return conversationRepository.save(newConv);
                });

        ConversationMessage userMsg = ConversationMessage.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(userMessage)
                .build();
        messageRepository.save(userMsg);
        conversation.setMessageCount(conversation.getMessageCount() + 1);
        
        return conversation;
    }

    /**
     * TRANSACTION 2: Lưu response bot
     */
    @Transactional
    public void saveBotMessage(Conversation conversation, String answer, long duration) {
        ConversationMessage botMsg = ConversationMessage.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(answer)
                .processingTimeMs((int) duration)
                .build();
        messageRepository.save(botMsg);
        conversation.setMessageCount(conversation.getMessageCount() + 1);
    }

    /**
     * Gọi Agent và review response (nếu bật)
     */
    private String generateResponse(String sessionId, String userId, String userMessage, String context) {
        // Gọi ShopAssistantAgent
        String response = shopAssistantAgent.chat(sessionId, userId, userMessage, context);

        // Nếu tắt review hoặc response rỗng -> trả về luôn
        if (!responseReviewEnabled || response == null || response.isBlank()) {
            return response;
        }

        // Review response
        try {
            long reviewStart = System.currentTimeMillis();
            ReviewResult reviewResult = responseReviewerAgent.review(response, context, userMessage);

            log.info("[ResponseReviewerAgent] Review: {}ms, approved: {}",
                    System.currentTimeMillis() - reviewStart,
                    reviewResult != null ? reviewResult.approved() : "null");

            if (reviewResult != null && !reviewResult.approved()) {
                log.warn("[ResponseReviewerAgent] REJECTED. Issues: {}", reviewResult.issues());
                // Log only, không retry để tránh latency và context bloat
            }
        } catch (Exception e) {
            log.warn("[ResponseReviewerAgent] Error: {}", e.getMessage());
        }

        return response;
    }

    private String getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "guest";
        }

        String username = auth.getName();

        return userRepository.findByUsername(username)
                .map(User::getUserId)
                .orElse("guest");
    }
}
