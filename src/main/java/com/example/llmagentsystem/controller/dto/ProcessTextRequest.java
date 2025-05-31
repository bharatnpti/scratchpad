package com.example.llmagentsystem.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProcessTextRequest {
    @NotBlank(message = "textInput cannot be blank")
    private String textInput;

    @NotBlank(message = "userPlatformId cannot be blank")
    private String userPlatformId;

    @NotBlank(message = "conversationPlatformId cannot be blank")
    private String conversationPlatformId;

    @NotBlank(message = "sourcePlatform cannot be blank")
    private String sourcePlatform; // e.g., "api", "slack", "msteams"
}
