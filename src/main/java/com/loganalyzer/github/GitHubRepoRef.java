package com.loganalyzer.github;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses github.com owner/repo from common git remote URL shapes.
 */
public record GitHubRepoRef(String owner, String repo) {

    private static final Pattern SSH = Pattern.compile("^git@github\\.com:([^/]+)/([^/]+?)(?:\\.git)?$");
    private static final Pattern HTTPS = Pattern.compile("^https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    public static Optional<GitHubRepoRef> parse(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            return Optional.empty();
        }
        String trimmed = gitUrl.trim();
        Matcher ssh = SSH.matcher(trimmed);
        if (ssh.matches()) {
            return Optional.of(new GitHubRepoRef(ssh.group(1), stripGit(ssh.group(2))));
        }
        Matcher https = HTTPS.matcher(trimmed);
        if (https.matches()) {
            return Optional.of(new GitHubRepoRef(https.group(1), stripGit(https.group(2))));
        }
        try {
            URI uri = URI.create(trimmed);
            if ("github.com".equalsIgnoreCase(uri.getHost()) && uri.getPath() != null) {
                String[] parts = uri.getPath().replaceFirst("^/", "").split("/");
                if (parts.length >= 2) {
                    return Optional.of(new GitHubRepoRef(parts[0], stripGit(parts[1])));
                }
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return Optional.empty();
    }

    public String fullName() {
        return owner + "/" + repo;
    }

    private static String stripGit(String name) {
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }
}
