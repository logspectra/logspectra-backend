package com.loganalyzer.github;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GitHubRepoRefTest {

    @Test
    void parsesHttpsGitSuffix() {
        var ref = GitHubRepoRef.parse("https://github.com/acme/gateway-service.git").orElseThrow();
        assertEquals("acme", ref.owner());
        assertEquals("gateway-service", ref.repo());
    }

    @Test
    void parsesSsh() {
        var ref = GitHubRepoRef.parse("git@github.com:acme/payment-service.git").orElseThrow();
        assertEquals("acme", ref.owner());
        assertEquals("payment-service", ref.repo());
    }

    @Test
    void rejectsNonGitHub() {
        assertTrue(GitHubRepoRef.parse("https://gitlab.com/acme/x.git").isEmpty());
    }
}
