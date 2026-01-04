package com.example.online_shoe_store.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiChatRequest {
    private String message;
    private String sessionId;
    private String userId;
}
