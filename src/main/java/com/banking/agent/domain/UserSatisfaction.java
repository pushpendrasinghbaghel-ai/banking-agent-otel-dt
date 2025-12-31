package com.banking.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User Satisfaction Feedback
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSatisfaction {
    private String sessionId;
    private String accountNumber;
    private String llmProvider;
    private String intent;
    private Double satisfactionScore; // 1-5 scale
    private String feedback;
    private Boolean wasHelpful;
    private Boolean wasAccurate;
    private String timestamp;
}
