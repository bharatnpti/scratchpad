package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.nlp.StructuredNlpResult;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DialogueState {
    private String conversationId;
    private boolean awaitingClarification;
    private String pendingIntent; // The original intent that needs clarification
    private List<String> missingEntities; // Names of entities that are missing
    private Map<String, Object> collectedEntities; // Entities collected so far for the pending intent
    private StructuredNlpResult originalNlpResult; // The original NLP result that triggered clarification

    public DialogueState(String conversationId) {
        this.conversationId = conversationId;
        this.awaitingClarification = false;
        this.collectedEntities = new java.util.HashMap<>();
    }

    public void startClarification(String intent, List<String> missingEntities, StructuredNlpResult originalNlpResult) {
        this.awaitingClarification = true;
        this.pendingIntent = intent;
        this.missingEntities = missingEntities;
        this.originalNlpResult = originalNlpResult;
        this.collectedEntities.clear(); // Clear previously collected entities for this clarification cycle
        if (originalNlpResult != null && originalNlpResult.entities() != null) {
            this.collectedEntities.putAll(originalNlpResult.entities());
        }
    }

    public void addClarifiedEntity(String entityName, Object entityValue) {
        this.collectedEntities.put(entityName, entityValue);
        if (this.missingEntities != null) {
            this.missingEntities.remove(entityName);
        }
    }

    public boolean isClarificationComplete() {
        return awaitingClarification && (missingEntities == null || missingEntities.isEmpty());
    }

    public void resetClarification() {
        this.awaitingClarification = false;
        this.pendingIntent = null;
        this.missingEntities = null;
        this.originalNlpResult = null;
        this.collectedEntities.clear();
    }
}
