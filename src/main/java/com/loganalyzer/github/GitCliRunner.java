package com.loganalyzer.github;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GitCliRunner {

    public void run(Path workingDir, Duration timeout, String... args) {
        run(workingDir, Map.of(), timeout, args);
    }

    public void run(Path workingDir, Map<String, String> extraEnv, Duration timeout, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().putAll(extraEnv);
        builder.redirectErrorStream(true);
        long started = System.currentTimeMillis();
        try {
            Process process = builder.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            process.getInputStream().transferTo(output);
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git " + redactArgs(args) + " timed out after " + timeout);
            }
            int code = process.exitValue();
            String text = output.toString(StandardCharsets.UTF_8);
            if (code != 0) {
                throw new IllegalStateException(
                    "git " + redactArgs(args) + " failed (exit " + code + "): " + truncate(text, 500));
            }
            log.debug("operation=gitCli feature=git-workspace status=exit cmd={} durationMs={}",
                redactArgs(args), System.currentTimeMillis() - started);
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git " + redactArgs(args) + " failed: " + ex.getMessage(), ex);
        }
    }

    private static String redactArgs(String... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String arg = args[i];
            if (arg != null && arg.contains("x-access-token:")) {
                sb.append("<redacted-url>");
            } else {
                sb.append(arg);
            }
        }
        return sb.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "…";
    }
}
