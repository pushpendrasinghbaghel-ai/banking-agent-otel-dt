package com.banking.agent.controller;

import com.banking.agent.domain.UserSatisfaction;
import com.banking.agent.service.LlmMonitoringService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for tracking user satisfaction and feedback
 */
@RestController
@RequestMapping("/api/feedback")
@Slf4j
public class UserSatisfactionController {

    private final LlmMonitoringService monitoringService;
    private final MeterRegistry meterRegistry;
    private final Counter satisfactionCounter;

    public UserSatisfactionController(LlmMonitoringService monitoringService, MeterRegistry meterRegistry) {
        this.monitoringService = monitoringService;
        this.meterRegistry = meterRegistry;
        this.satisfactionCounter = Counter.builder("user.satisfaction.submissions")
                .description("Total user satisfaction submissions")
                .register(meterRegistry);
    }

    /**
     * Submit user satisfaction feedback
     */
    @PostMapping("/satisfaction")
    public ResponseEntity<Map<String, String>> submitSatisfaction(@RequestBody UserSatisfaction satisfaction) {
        log.info("Received user satisfaction feedback - Score: {}, Provider: {}, Intent: {}", 
                satisfaction.getSatisfactionScore(), satisfaction.getLlmProvider(), satisfaction.getIntent());

        // Normalize satisfaction score to 0-1 scale for correctness tracking
        double normalizedScore = satisfaction.getSatisfactionScore() / 5.0;
        
        // Record correctness based on user satisfaction
        monitoringService.recordCorrectness(
                satisfaction.getLlmProvider(),
                satisfaction.getIntent(),
                normalizedScore,
                satisfaction.getFeedback()
        );

        // Record business event for user satisfaction
        Map<String, String> attributes = new HashMap<>();
        attributes.put("provider", satisfaction.getLlmProvider());
        attributes.put("intent", satisfaction.getIntent());
        attributes.put("score", String.valueOf(satisfaction.getSatisfactionScore()));
        attributes.put("helpful", String.valueOf(satisfaction.getWasHelpful()));
        attributes.put("accurate", String.valueOf(satisfaction.getWasAccurate()));
        if (satisfaction.getAccountNumber() != null) {
            attributes.put("account", satisfaction.getAccountNumber());
        }
        if (satisfaction.getSessionId() != null) {
            attributes.put("session", satisfaction.getSessionId());
        }
        
        monitoringService.recordBusinessEvent("user_satisfaction_submitted", attributes);

        // Record metrics
        satisfactionCounter.increment();
        
        // Record gauge for current satisfaction level
        meterRegistry.gauge("user.satisfaction.score",
                java.util.List.of(
                        io.micrometer.core.instrument.Tag.of("provider", satisfaction.getLlmProvider()),
                        io.micrometer.core.instrument.Tag.of("intent", satisfaction.getIntent())
                ),
                satisfaction.getSatisfactionScore());

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Thank you for your feedback!");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Submit quick feedback (thumbs up/down)
     */
    @PostMapping("/quick-feedback")
    public ResponseEntity<Map<String, String>> submitQuickFeedback(
            @RequestParam String sessionId,
            @RequestParam String llmProvider,
            @RequestParam String intent,
            @RequestParam boolean helpful) {
        
        log.info("Received quick feedback - Helpful: {}, Provider: {}, Intent: {}", 
                helpful, llmProvider, intent);

        // Convert to satisfaction score
        double score = helpful ? 1.0 : 0.0;
        
        monitoringService.recordCorrectness(
                llmProvider,
                intent,
                score,
                helpful ? "User found response helpful" : "User found response unhelpful"
        );

        Map<String, String> attributes = new HashMap<>();
        attributes.put("provider", llmProvider);
        attributes.put("intent", intent);
        attributes.put("helpful", String.valueOf(helpful));
        attributes.put("session", sessionId);
        
        monitoringService.recordBusinessEvent("quick_feedback_submitted", attributes);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Feedback recorded");

        return ResponseEntity.ok(response);
    }

    /**
     * Report incorrect or inaccurate response
     */
    @PostMapping("/report-issue")
    public ResponseEntity<Map<String, String>> reportIssue(
            @RequestParam String sessionId,
            @RequestParam String llmProvider,
            @RequestParam String intent,
            @RequestParam String issueType,
            @RequestParam(required = false) String description) {
        
        log.warn("Issue reported - Type: {}, Provider: {}, Intent: {}, Description: {}", 
                issueType, llmProvider, intent, description);

        // Record as low correctness
        monitoringService.recordCorrectness(
                llmProvider,
                intent,
                0.0,
                "Issue reported: " + issueType + (description != null ? " - " + description : "")
        );

        Map<String, String> attributes = new HashMap<>();
        attributes.put("provider", llmProvider);
        attributes.put("intent", intent);
        attributes.put("issue_type", issueType);
        attributes.put("session", sessionId);
        if (description != null) {
            attributes.put("description", description);
        }
        
        monitoringService.recordBusinessEvent("issue_reported", attributes);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Issue reported. Thank you for helping us improve!");

        return ResponseEntity.ok(response);
    }
}
