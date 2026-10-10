package com.loganalyzer.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.loganalyzer.github.GitHubGitClient;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Step 2+ of the agent loop: fetch a known path into memory for analysis / patching.
 */
@Component
@RequiredArgsConstructor
public class GetFileContentTool implements AgentTool {

    private final GitHubGitClient gitHubGitClient;

    @Override
    public String name() {
        return "get_file_content";
    }

    @Override
    public String description() {
        return "Fetch the full UTF-8 contents of one file by exact repo-relative path "
            + "(from list_repo_files or a stack-trace path). Call again for more files as needed.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "path", Map.of(
                    "type", "string",
                    "description", "Exact path, e.g. src/main/java/com/example/Foo.java"
                )
            ),
            "required", java.util.List.of("path")
        );
    }

    @Override
    public String execute(AgentSession session, JsonNode args) {
        String path = args != null && args.has("path") ? args.get("path").asText("").trim() : "";
        if (!StringUtils.hasText(path) || path.contains("..")) {
            return "Error: invalid path";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        Optional<String> content = gitHubGitClient.getFileContent(session.getRepo(), path, session.getRef());
        if (content.isEmpty()) {
            session.recordTrace("get_file_content path=" + path + " notFound=true");
            return "File not found: " + path + ". Use list_repo_files to discover valid paths.";
        }

        String body = content.get();
        int max = session.getMaxFileChars();
        if (body.length() > max) {
            body = body.substring(0, max) + "\n\n/* … truncated after " + max + " chars … */";
        }
        session.markFetched(path);
        session.recordTrace("get_file_content path=" + path + " chars=" + body.length());
        return "FILE: " + path + "\n```\n" + body + "\n```";
    }
}
