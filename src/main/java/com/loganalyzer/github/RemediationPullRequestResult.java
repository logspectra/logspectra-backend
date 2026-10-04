package com.loganalyzer.github;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RemediationPullRequestResult {
    String owner;
    String repo;
    String branch;
    String baseBranch;
    String pullRequestUrl;
    Integer pullRequestNumber;
    String commitSha;
}
