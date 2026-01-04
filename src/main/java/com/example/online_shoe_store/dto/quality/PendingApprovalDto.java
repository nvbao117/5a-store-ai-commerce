package com.example.online_shoe_store.dto.quality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for pending approval requests in Human-in-the-Loop workflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingApprovalDto {
    private String requestId;
    private String actionType;
    private String sessionId;
    private Map<String, Object> payload;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String approverNote;
}
