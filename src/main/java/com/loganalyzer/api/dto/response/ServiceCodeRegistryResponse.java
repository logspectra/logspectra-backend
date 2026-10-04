package com.loganalyzer.api.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ServiceCodeRegistryResponse {
    String projectId;
    String serviceName;
    String gitUrl;
    String localPath;
    String defaultBranch;
    List<String> sourceRoots;
    Instant registeredAt;
    String redisKey;
}
