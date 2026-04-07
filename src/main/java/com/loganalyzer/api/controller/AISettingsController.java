package com.loganalyzer.api.controller;

import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.api.dto.request.AISettingsRequest;
import com.loganalyzer.api.dto.response.AISettingsResponse;
import com.loganalyzer.api.dto.response.ProviderModelsResponse;
import com.loganalyzer.service.AISettingsService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/settings")
@RequiredArgsConstructor
public class AISettingsController {

    private final AISettingsService aiSettingsService;

    @PostMapping
    public ResponseEntity<AISettingsResponse> createOrUpdate(@Valid @RequestBody AISettingsRequest request) {
        return ResponseEntity.ok(aiSettingsService.createOrUpdate(request));
    }

    @GetMapping
    public ResponseEntity<AISettingsResponse> getActive() {
        return ResponseEntity.ok(aiSettingsService.getActive());
    }


    @GetMapping("/providers/{provider}/models")
    public ResponseEntity<ProviderModelsResponse> getProviderModels(@PathVariable String provider) {
        return ResponseEntity.ok(aiSettingsService.getModels(AIProvider.from(provider)));
    }

    @GetMapping("/providers/models")
    public ResponseEntity<ProviderModelsResponse> getProviderModelsByQuery(@RequestParam("provider") String provider) {
        return ResponseEntity.ok(aiSettingsService.getModels(AIProvider.from(provider)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AISettingsResponse> update(@PathVariable UUID id, @Valid @RequestBody AISettingsRequest request) {
        return ResponseEntity.ok(aiSettingsService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        aiSettingsService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<AISettingsResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(aiSettingsService.activate(id));
    }
}
