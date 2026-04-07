package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loganalyzer.api.dto.request.SeverityRuleUpsertRequest;
import com.loganalyzer.api.dto.response.SeverityRuleResponse;
import com.loganalyzer.domain.model.SeverityRule;
import com.loganalyzer.domain.repository.SeverityRuleRepository;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeverityRuleServiceTest {

    @Mock
    private SeverityRuleRepository severityRuleRepository;

    @InjectMocks
    private SeverityRuleService severityRuleService;

    @Test
    void createShouldNormalizeAndPersist() {
        SeverityRuleUpsertRequest request = new SeverityRuleUpsertRequest();
        request.setRuleName("  Login error rule  ");
        request.setExceptionPattern("  NullPointerException.*  ");
        request.setServiceName("  auth-service  ");
        request.setSeverity("high");

        UUID id = UUID.randomUUID();
        when(severityRuleRepository.save(org.mockito.ArgumentMatchers.any(SeverityRule.class)))
            .thenAnswer(invocation -> {
                SeverityRule saved = invocation.getArgument(0);
                saved.setId(id);
                saved.setCreatedAt(Instant.parse("2026-03-28T00:00:00Z"));
                saved.setUpdatedAt(Instant.parse("2026-03-28T00:00:00Z"));
                return saved;
            });

        SeverityRuleResponse response = severityRuleService.createSeverityRule(request);

        assertEquals(id, response.getId());
        assertEquals("Login error rule", response.getRuleName());
        assertEquals("NullPointerException.*", response.getExceptionPattern());
        assertEquals("auth-service", response.getServiceName());
        assertEquals("HIGH", response.getSeverity());
    }

    @Test
    void listShouldMapEntities() {
        SeverityRule rule = SeverityRule.builder()
            .id(UUID.randomUUID())
            .ruleName("Rule")
            .severity("LOW")
            .build();
        when(severityRuleRepository.findAll()).thenReturn(List.of(rule));

        List<SeverityRuleResponse> response = severityRuleService.getSeverityRules();

        assertEquals(1, response.size());
        assertEquals("Rule", response.getFirst().getRuleName());
        assertEquals("LOW", response.getFirst().getSeverity());
    }

    @Test
    void updateShouldThrowWhenMissing() {
        UUID id = UUID.randomUUID();
        SeverityRuleUpsertRequest request = new SeverityRuleUpsertRequest();
        request.setRuleName("Rule");
        request.setSeverity("LOW");

        when(severityRuleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> severityRuleService.updateSeverityRule(id, request));
    }

    @Test
    void createShouldConvertBlankOptionalFieldsToNull() {
        SeverityRuleUpsertRequest request = new SeverityRuleUpsertRequest();
        request.setRuleName("Rule");
        request.setExceptionPattern("   ");
        request.setServiceName("");
        request.setSeverity("MEDIUM");

        when(severityRuleRepository.save(org.mockito.ArgumentMatchers.any(SeverityRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        SeverityRuleResponse response = severityRuleService.createSeverityRule(request);

        assertNull(response.getExceptionPattern());
        assertNull(response.getServiceName());
    }

    @Test
    void deleteShouldRemoveExistingRule() {
        UUID id = UUID.randomUUID();
        SeverityRule entity = SeverityRule.builder().id(id).ruleName("Rule").severity("LOW").build();
        when(severityRuleRepository.findById(id)).thenReturn(Optional.of(entity));

        severityRuleService.deleteSeverityRule(id);

        verify(severityRuleRepository).delete(entity);
    }

    @Test
    void resolveSeverityShouldUseMatchingRuleBeforeDefault() {
        SeverityRule rule = SeverityRule.builder()
            .ruleName("NPE in auth")
            .serviceName("auth-service")
            .exceptionPattern("NullPointerException")
            .severity("CRITICAL")
            .build();

        String severity = severityRuleService.resolveSeverity(
            List.of(rule),
            "java.lang.NullPointerException: boom",
            "auth-service",
            "ERROR"
        );

        assertEquals("CRITICAL", severity);
    }

    @Test
    void resolveSeverityShouldApplyDefaultMappingWhenNoRuleMatches() {
        SeverityRule unrelatedRule = SeverityRule.builder()
            .ruleName("Other")
            .serviceName("payment-service")
            .exceptionPattern("TimeoutException")
            .severity("LOW")
            .build();

        assertEquals("LOW", severityRuleService.resolveSeverity(List.of(unrelatedRule), "anything", "auth-service", "INFO"));
        assertEquals("MEDIUM", severityRuleService.resolveSeverity(List.of(unrelatedRule), "anything", "auth-service", "WARN"));
        assertEquals("HIGH", severityRuleService.resolveSeverity(List.of(unrelatedRule), "anything", "auth-service", "ERROR"));
        assertEquals("CRITICAL", severityRuleService.resolveSeverity(List.of(unrelatedRule), "anything", "auth-service", "FATAL"));
    }

    @Test
    void resolveSeverityShouldPreferMoreSpecificRule() {
        SeverityRule serviceOnly = SeverityRule.builder()
            .ruleName("Auth default")
            .serviceName("auth-service")
            .severity("MEDIUM")
            .build();
        SeverityRule serviceAndException = SeverityRule.builder()
            .ruleName("Auth npe")
            .serviceName("auth-service")
            .exceptionPattern("NullPointerException")
            .severity("CRITICAL")
            .build();

        String severity = severityRuleService.resolveSeverity(
            List.of(serviceOnly, serviceAndException),
            "NullPointerException",
            "auth-service",
            "ERROR"
        );

        assertEquals("CRITICAL", severity);
    }
}

