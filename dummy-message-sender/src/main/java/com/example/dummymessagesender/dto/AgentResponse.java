package com.example.dummymessagesender.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {
    private String responseText;
    private String userPlatformId;
    private String conversationPlatformId;
}
