package com.loganalyzer.service;

import com.loganalyzer.api.dto.request.SeverityRuleUpsertRequest;
import com.loganalyzer.api.dto.response.SeverityRuleResponse;
import com.loganalyzer.domain.model.SeverityRule;
import com.loganalyzer.domain.repository.SeverityRuleRepository;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SeverityRuleService {

    private final SeverityRuleRepository severityRuleRepository;

    @Transactional(readOnly = true)
    public List<SeverityRuleResponse> getSeverityRules() {
        return severityRuleRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public SeverityRuleResponse createSeverityRule(SeverityRuleUpsertRequest request) {
        SeverityRule rule = SeverityRule.builder().build();
        mapRequest(rule, request);
        return toResponse(severityRuleRepository.save(rule));
    }

    @Transactional
    public SeverityRuleResponse updateSeverityRule(UUID id, SeverityRuleUpsertRequest request) {
        SeverityRule existing = severityRuleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Severity rule not found: " + id));
        mapRequest(existing, request);
        return toResponse(severityRuleRepository.save(existing));
    }

    @Transactional
    public void deleteSeverityRule(UUID id) {
        SeverityRule existing = severityRuleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Severity rule not found: " + id));
        severityRuleRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public List<SeverityRule> getRulesForResolution() {
        return severityRuleRepository.findAll();
    }

    public String resolveSeverity(List<SeverityRule> rules, String exception, String service, String level) {
        SeverityRule matched = findBestMatchingRule(rules, exception, service);
        if (matched != null && StringUtils.hasText(matched.getSeverity())) {
            return matched.getSeverity().trim().toUpperCase(Locale.ENGLISH);
        }
        return defaultSeverityFromLevel(level);
    }

    private SeverityRule findBestMatchingRule(List<SeverityRule> rules, String exception, String service) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        SeverityRule best = null;
        int bestScore = -1;

        for (SeverityRule rule : rules) {
            if (!matchesRule(rule, exception, service)) {
                continue;
            }
            int score = specificityScore(rule);
            if (score > bestScore) {
                best = rule;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean matchesRule(SeverityRule rule, String exception, String service) {
        boolean serviceMatches = matchesService(rule.getServiceName(), service);
        boolean exceptionMatches = matchesException(rule.getExceptionPattern(), exception);
        return serviceMatches && exceptionMatches;
    }

    private boolean matchesService(String ruleServiceName, String service) {
        if (!StringUtils.hasText(ruleServiceName)) {
            return true;
        }
        return StringUtils.hasText(service) && ruleServiceName.trim().equalsIgnoreCase(service.trim());
    }

    private boolean matchesException(String ruleExceptionPattern, String exception) {
        if (!StringUtils.hasText(ruleExceptionPattern)) {
            return true;
        }
        if (!StringUtils.hasText(exception)) {
            return false;
        }

        String pattern = ruleExceptionPattern.trim();
        String value = exception.trim();
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(value).find();
        } catch (PatternSyntaxException ignored) {
            return value.toLowerCase(Locale.ENGLISH).contains(pattern.toLowerCase(Locale.ENGLISH));
        }
    }

    private int specificityScore(SeverityRule rule) {
        int score = 0;
        if (StringUtils.hasText(rule.getServiceName())) {
            score += 2;
        }
        if (StringUtils.hasText(rule.getExceptionPattern())) {
            score += 1;
        }
        return score;
    }

    private String defaultSeverityFromLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return "LOW";
        }
        return switch (level.trim().toUpperCase(Locale.ENGLISH)) {
            case "TRACE", "DEBUG", "INFO" -> "LOW";
            case "WARN" -> "MEDIUM";
            case "ERROR" -> "HIGH";
            case "FATAL" -> "CRITICAL";
            default -> "LOW";
        };
    }

    private void mapRequest(SeverityRule target, SeverityRuleUpsertRequest source) {
        target.setRuleName(source.getRuleName().trim());
        target.setExceptionPattern(normalizeNullable(source.getExceptionPattern()));
        target.setServiceName(normalizeNullable(source.getServiceName()));
        target.setSeverity(source.getSeverity().trim().toUpperCase(Locale.ENGLISH));
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private SeverityRuleResponse toResponse(SeverityRule rule) {
        return SeverityRuleResponse.builder()
            .id(rule.getId())
            .ruleName(rule.getRuleName())
            .exceptionPattern(rule.getExceptionPattern())
            .serviceName(rule.getServiceName())
            .severity(rule.getSeverity())
            .createdAt(rule.getCreatedAt())
            .updatedAt(rule.getUpdatedAt())
            .build();
    }
}

