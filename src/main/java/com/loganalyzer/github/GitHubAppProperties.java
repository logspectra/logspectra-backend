package com.loganalyzer.github;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "github")
public class GitHubAppProperties {

    private App app = new App();
    private Pr pr = new Pr();

    @Data
    public static class App {
        /** Master switch for GitHub App integration. */
        private boolean enabled = false;
        private String appId = "";
        /**
         * PEM private key body (prefer env GITHUB_APP_PRIVATE_KEY with literal newlines or \\n).
         * Leave empty if using privateKeyPath.
         */
        private String privateKey = "";
        /** Absolute or relative path to the .pem file downloaded from GitHub App settings. */
        private String privateKeyPath = "";
        /**
         * Installation id for the org/account where the App is installed.
         * Find via: GET /app/installations (with App JWT) or App settings → Installations.
         */
        private String installationId = "";
        private String apiBaseUrl = "https://api.github.com";
    }

    @Data
    public static class Pr {
        /**
         * When true (and app.enabled), analysis with createRemediationPr=true or
         * autoCreateAfterAnalysis=true opens a PR in the service's registered git repo.
         */
        private boolean enabled = true;
        private boolean autoCreateAfterAnalysis = false;
        private String branchPrefix = "logspectra/fix";
        private String commitAuthorName = "LogSpectra";
        private String commitAuthorEmail = "logspectra@users.noreply.github.com";
        private String reviewers = "";
    }
}
