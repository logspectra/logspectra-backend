package com.loganalyzer.config;

import com.loganalyzer.github.GitHubAppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GitHubAppProperties.class)
public class GitHubConfig {
}
