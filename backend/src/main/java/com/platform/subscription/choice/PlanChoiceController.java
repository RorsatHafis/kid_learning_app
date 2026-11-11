package com.platform.subscription.choice;

import com.platform.identity.UiLanguage;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2026-11-11: the signed-in person's own plan choice (made before registering) - read to offer its checkout first,
 * or set aside to keep the free plan. Only ever the caller's own row; it grants nothing.
 */
@RestController
public class PlanChoiceController {

    private final PlanChoices choices;

    public PlanChoiceController(PlanChoices choices) {
        this.choices = choices;
    }

    /** 200 with the chosen plan as its record holds it now; 204 when nothing is waiting. */
    @GetMapping("/api/v1/account/plan-choice")
    public ResponseEntity<PlanChoices.ChosenPlan> mine(@AuthenticationPrincipal UUID accountId) {
        return choices.current(accountId, UiLanguage.ofCurrentRequest())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Keep the free plan instead (a payment already started is the person's own to finish or cancel). */
    @DeleteMapping("/api/v1/account/plan-choice")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setAside(@AuthenticationPrincipal UUID accountId) {
        choices.setAside(accountId);
    }
}
