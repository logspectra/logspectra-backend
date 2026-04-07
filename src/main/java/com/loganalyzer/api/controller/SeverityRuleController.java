package com.loganalyzer.api.controller;

import com.loganalyzer.api.dto.request.SeverityRuleUpsertRequest;
import com.loganalyzer.api.dto.response.SeverityRuleResponse;
import com.loganalyzer.service.SeverityRuleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/severity-rules")
@RequiredArgsConstructor
public class SeverityRuleController {

    private final SeverityRuleService severityRuleService;

    @GetMapping
    public ResponseEntity<List<SeverityRuleResponse>> getSeverityRules() {
        return ResponseEntity.ok(severityRuleService.getSeverityRules());
    }

    @PostMapping
    public ResponseEntity<SeverityRuleResponse> createSeverityRule(@Valid @RequestBody SeverityRuleUpsertRequest request) {
        return ResponseEntity.ok(severityRuleService.createSeverityRule(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SeverityRuleResponse> updateSeverityRule(@PathVariable UUID id, @Valid @RequestBody SeverityRuleUpsertRequest request) {
        return ResponseEntity.ok(severityRuleService.updateSeverityRule(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeverityRule(@PathVariable UUID id) {
        severityRuleService.deleteSeverityRule(id);
        return ResponseEntity.noContent().build();
    }
}

