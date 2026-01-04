package com.example.online_shoe_store.Service.ai.memory;

import com.example.online_shoe_store.Entity.Conversation;
import com.example.online_shoe_store.Entity.ConversationMessage;
import com.example.online_shoe_store.Repository.ConversationMessageRepository;
import com.example.online_shoe_store.Repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncSummarizationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ContextSummarizerAgent contextSummarizerAgent;
    
    // Ngưỡng số message mới trước khi cần re-summarize
    private static final int SUMMARIZE_THRESHOLD = 6;
    // Số message gần nhất KHÔNG summarize (giữ nguyên trong context)
    private static final int RECENT_MESSAGES_TO_KEEP = 4;
    // Overlap window để tránh mất ngữ cảnh giữa các lần summarize
    private static final int OVERLAP_WINDOW = 2;

    @Async
    @Transactional
    public void triggerSummarizationIfNeeded(String sessionId) {
        try {
            var conversationOpt = conversationRepository.findBySessionIdAndIsActiveTrue(sessionId);
            if (conversationOpt.isEmpty()) {
                return;
            }
            
            Conversation conversation = conversationOpt.get();
            int currentCount = conversation.getMessageCount() != null ? conversation.getMessageCount() : 0;
            int lastSummarized = conversation.getLastSummarizedCount() != null ? conversation.getLastSummarizedCount() : 0;
            
            // Chỉ summarize nếu có đủ message mới
            int newMessages = currentCount - lastSummarized;
            if (newMessages < SUMMARIZE_THRESHOLD) {
                return;
            }
            
            log.info("[AsyncSummarization] Starting for session: {} (new msgs: {})", sessionId, newMessages);
            long startTime = System.currentTimeMillis();
            
            List<ConversationMessage> messages = messageRepository
                    .findByConversationSessionIdOrderByCreatedAtAsc(sessionId);
            
            if (messages.size() <= RECENT_MESSAGES_TO_KEEP) {
                return;
            }
            
            int newCutoff = messages.size() - RECENT_MESSAGES_TO_KEEP;
            
            int deltaStart = Math.max(0, lastSummarized - OVERLAP_WINDOW);
            
            if (deltaStart >= newCutoff) {
                log.debug("[AsyncSummarization] No new delta to summarize");
                return;
            }
            
            List<ConversationMessage> deltaMessages = messages.subList(deltaStart, newCutoff);
            String deltaConversation = formatMessages(deltaMessages);
            
            String existingSummary = conversation.getSummary();
            String inputForSummary;
            
            if (existingSummary != null && !existingSummary.isBlank()) {
                inputForSummary = "CURRENT SUMMARY:\n" + existingSummary + 
                        "\n\nNEW CONVERSATION TO ADD (incorporate into summary):\n" + deltaConversation;
            } else {
                inputForSummary = deltaConversation;
            }
            
            String newSummary = contextSummarizerAgent.summarize(inputForSummary);
            
            conversation.setSummary(newSummary);
            conversation.setLastSummarizedCount(newCutoff);
            conversationRepository.save(conversation);
        } catch (Exception e) {
            log.error("[AsyncSummarization] Error: {}", e.getMessage());
        }
    }
    
    private String formatMessages(List<ConversationMessage> messages) {
        return messages.stream()
                .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                .collect(Collectors.joining("\n"));
    }
}
