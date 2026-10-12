package com.loganalyzer.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.loganalyzer.github.GitHubGitClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Step 1 of the agent loop: discover files in the live GitHub tree (no clone).
 */
@Component
@RequiredArgsConstructor
public class ListRepoFilesTool implements AgentTool {

    private final GitHubGitClient gitHubGitClient;

    @Override
    public String name() {
        return "list_repo_files";
    }

    @Override
    public String description() {
        return "List source file paths in the repository (recursive tree). "
            + "Call this first. Optionally filter by pathPrefix (e.g. src/main/java) or query substring.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("pathPrefix", Map.of(
            "type", "string",
            "description", "Optional directory prefix, e.g. src/main/java"
        ));
        props.put("query", Map.of(
            "type", "string",
            "description", "Optional case-insensitive substring match on path (class/file name)"
        ));
        return Map.of(
            "type", "object",
            "properties", props
        );
    }

    @Override
    public String execute(AgentSession session, JsonNode args) {
        List<String> tree = session.getCachedTree();
        if (tree.isEmpty()) {
            tree = gitHubGitClient.listFilePaths(session.getRepo(), session.getRef(), session.getMaxTreeEntries());
            session.setCachedTree(tree);
        }

        String prefix = text(args, "pathPrefix");
        String query = text(args, "query");
        String queryLower = query == null ? null : query.toLowerCase(Locale.ROOT);

        List<String> filtered = tree.stream()
            .filter(path -> !StringUtils.hasText(prefix) || path.startsWith(prefix) || path.contains("/" + prefix))
            .filter(path -> queryLower == null || path.toLowerCase(Locale.ROOT).contains(queryLower))
            .limit(session.getMaxTreeEntries())
            .collect(Collectors.toList());

        session.recordTrace("list_repo_files prefix=" + prefix + " query=" + query + " matched=" + filtered.size());
        if (filtered.isEmpty()) {
            return "No files matched. Total tree size=" + tree.size()
                + ". Try a broader query or omit filters.";
        }
        return "Matched " + filtered.size() + " file(s):\n" + String.join("\n", filtered);
    }

    private static String text(JsonNode args, String field) {
        if (args == null || !args.has(field) || args.get(field).isNull()) {
            return null;
        }
        String value = args.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
