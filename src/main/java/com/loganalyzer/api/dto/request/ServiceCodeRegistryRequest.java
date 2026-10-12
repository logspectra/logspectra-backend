package com.loganalyzer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class ServiceCodeRegistryRequest {

    @NotBlank
    private String projectId;

    @NotBlank
    private String serviceName;

    private String gitUrl;

    /** Path on the backend host (or shared volume), not the client container path unless shared. */
    private String localPath;

    private String defaultBranch = "main";

    private List<String> sourceRoots;
}
