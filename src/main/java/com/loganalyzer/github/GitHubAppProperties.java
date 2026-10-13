package com.loganalyzer.github;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "github")
public class GitHubAppProperties {

    private App app = new App();
    private Pr pr = new Pr();
    private Agent agent = new Agent();

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
        /**
         * When true (default), one branch per service + error fingerprint — repeat Debug amends that
         * branch and reuses an open PR instead of creating logspectra/fix-...-&lt;timestamp&gt; each time.
         */
        private boolean reuseBranchPerFingerprint = true;
        private String commitAuthorName = "LogSpectra";
        private String commitAuthorEmail = "logspectra@users.noreply.github.com";
        private String reviewers = "";
    }

    /**
     * Iterative GitHub tool-calling agent (list files → fetch by path → refine → JSON fix).
     * Multi-step reasoning over live repo state — not ML model self-training.
     */
    @Data
    public static class Agent {
        /** When true (and app.enabled), OpenAI analysis uses the tool loop when a gitUrl is registered. */
        private boolean toolCallingEnabled = true;
        private int maxIterations = 8;
        /** Cap recursive tree listing returned to the model. */
        private int maxTreeEntries = 400;
        /** Truncate file content returned by get_file_content. */
        private int maxFileChars = 60_000;
    }
}
