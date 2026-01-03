package com.example.online_shoe_store.Service.ai.quality;

import com.example.online_shoe_store.dto.quality.PendingApprovalDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Service xử lý Human-in-the-Loop approval
 * Dùng cho các action nhạy cảm như hủy đơn, hoàn tiền
 */
@Service
@Slf4j
public class HumanApprovalService {
    
    private final ConcurrentMap<String, CompletableFuture<String>> pendingApprovals = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingApprovalDto> pendingRequests = new ConcurrentHashMap<>();
    
    // Timeout for approval (5 minutes)
    private static final long APPROVAL_TIMEOUT_MINUTES = 5;
    
    /**
     * Queue a request for human approval
     * @param actionType Type of action (CANCEL_ORDER, REFUND, etc.)
     * @param sessionId Current chat session
     * @param payload Action payload (orderId, amount, etc.)
     * @return Request ID for tracking
     */
    public String queueForApproval(String actionType, String sessionId, Map<String, Object> payload) {
        String requestId = UUID.randomUUID().toString();
        
        PendingApprovalDto request = PendingApprovalDto.builder()
                .requestId(requestId)
                .actionType(actionType)
                .sessionId(sessionId)
                .payload(payload)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        
        pendingRequests.put(requestId, request);
        pendingApprovals.put(requestId, new CompletableFuture<>());
        
        log.info("[HITL] Queued approval request: {} for action: {}", requestId, actionType);
        
        // TODO: Send WebSocket notification to admin dashboard
        // messagingTemplate.convertAndSend("/topic/approvals", request);
        
        return requestId;
    }
    
    /**
     * Wait for approval with timeout
     * @param requestId Request ID to wait for
     * @return Approval response or timeout message
     */
    public String waitForApproval(String requestId) {
        CompletableFuture<String> future = pendingApprovals.get(requestId);
        if (future == null) {
            return "Không tìm thấy yêu cầu phê duyệt.";
        }
        
        try {
            return future.get(APPROVAL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            log.warn("[HITL] Approval timeout for request: {}", requestId);
            cleanup(requestId);
            return "Yêu cầu đã hết hạn do không được phê duyệt trong thời gian quy định.";
        } catch (InterruptedException | ExecutionException e) {
            log.error("[HITL] Error waiting for approval: {}", e.getMessage());
            cleanup(requestId);
            return "Có lỗi xảy ra khi chờ phê duyệt. Vui lòng thử lại.";
        }
    }
    
    /**
     * Approve a pending request (called by admin)
     * @param requestId Request ID
     * @param approverNote Optional note from approver
     */
    public void approve(String requestId, String approverNote) {
        CompletableFuture<String> future = pendingApprovals.get(requestId);
        if (future != null) {
            PendingApprovalDto request = pendingRequests.get(requestId);
            String response = "✅ Yêu cầu đã được phê duyệt.";
            if (approverNote != null && !approverNote.isBlank()) {
                response += " Ghi chú: " + approverNote;
            }
            future.complete(response);
            
            if (request != null) {
                request.setStatus("APPROVED");
                request.setResolvedAt(LocalDateTime.now());
            }
            
            log.info("[HITL] Approved request: {}", requestId);
        }
    }
    
    /**
     * Reject a pending request (called by admin)
     * @param requestId Request ID
     * @param reason Rejection reason
     */
    public void reject(String requestId, String reason) {
        CompletableFuture<String> future = pendingApprovals.get(requestId);
        if (future != null) {
            String response = "❌ Yêu cầu đã bị từ chối.";
            if (reason != null && !reason.isBlank()) {
                response += " Lý do: " + reason;
            }
            future.complete(response);
            
            PendingApprovalDto request = pendingRequests.get(requestId);
            if (request != null) {
                request.setStatus("REJECTED");
                request.setResolvedAt(LocalDateTime.now());
            }
            
            log.info("[HITL] Rejected request: {} - Reason: {}", requestId, reason);
        }
    }
    
    /**
     * Check if action requires approval
     * @param actionType Type of action
     * @return true if approval required
     */
    public boolean requiresApproval(String actionType) {
        return switch (actionType.toUpperCase()) {
            case "CANCEL_ORDER", "REFUND", "DELETE_ACCOUNT", "MODIFY_PAYMENT" -> true;
            default -> false;
        };
    }
    
    /**
     * Get all pending requests for admin dashboard
     */
    public Map<String, PendingApprovalDto> getPendingRequests() {
        return Map.copyOf(pendingRequests);
    }
    
    private void cleanup(String requestId) {
        pendingApprovals.remove(requestId);
        PendingApprovalDto request = pendingRequests.get(requestId);
        if (request != null) {
            request.setStatus("EXPIRED");
        }
    }
}
