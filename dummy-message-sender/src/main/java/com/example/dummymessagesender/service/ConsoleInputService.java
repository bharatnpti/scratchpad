package com.example.dummymessagesender.service;

import com.example.dummymessagesender.dto.ProcessTextRequest;
import org.springframework.stereotype.Service;
import java.util.Scanner;

@Service
public class ConsoleInputService {

    private final Scanner scanner = new Scanner(System.in);

    public ProcessTextRequest gatherInputLoop() {
        System.out.println("\nEnter text input for the agent (or type 'exit' to quit):");
        String textInput = scanner.nextLine();
        if ("exit".equalsIgnoreCase(textInput)) {
            return null; // Signal to exit
        }

        System.out.println("Enter user platform ID (e.g., user123):");
        String userPlatformId = scanner.nextLine();

        System.out.println("Enter conversation platform ID (e.g., conv789):");
        String conversationPlatformId = scanner.nextLine();

        System.out.println("Enter source platform (e.g., dummy-java-app, api):");
        String sourcePlatform = scanner.nextLine();

        return new ProcessTextRequest(textInput, userPlatformId, conversationPlatformId, sourcePlatform);
    }
}
