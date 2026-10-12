package com.loganalyzer.api.controller;

import com.loganalyzer.api.dto.request.ServiceCodeRegistryRequest;
import com.loganalyzer.api.dto.response.ServiceCodeRegistryResponse;
import com.loganalyzer.service.ServiceCodeRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceCodeRegistryController {

    private final ServiceCodeRegistryService serviceCodeRegistryService;

    @Value("${logspectra.ai.registry.token:}")
    private String registryToken;

    @PostMapping("/code-registry")
    public ResponseEntity<ServiceCodeRegistryResponse> register(
        @Valid @RequestBody ServiceCodeRegistryRequest request,
        @RequestHeader(value = "X-LogSpectra-Registry-Token", required = false) String token
    ) {
        if (StringUtils.hasText(registryToken) && !registryToken.equals(token)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(serviceCodeRegistryService.register(request));
    }
}
