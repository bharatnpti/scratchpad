package com.example.llmagentsystem.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AgentResponse {
    private String responseText;
    private String userPlatformId;
    private String conversationPlatformId;
}
