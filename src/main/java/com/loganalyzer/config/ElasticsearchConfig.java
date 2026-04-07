package com.loganalyzer.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.uris}")
    private String[] uris;

    @Value("${elasticsearch.username}")
    private String username;

    @Value("${elasticsearch.password}")
    private String password;

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
            new UsernamePasswordCredentials(username, password));

        return RestClient.builder(java.util.Arrays.stream(uris).map(HttpHost::create).toArray(HttpHost[]::new))
            .setHttpClientConfigCallback(builder -> builder
                .setDefaultCredentialsProvider(credentialsProvider)
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(30))
            .setRequestConfigCallback(builder -> builder
                .setConnectTimeout(5_000)
                .setSocketTimeout(30_000))
            .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient, ObjectMapper objectMapper) {
        ElasticsearchTransport transport = new RestClientTransport(
            restClient,
            new JacksonJsonpMapper(objectMapper.copy())
        );
        return new ElasticsearchClient(transport);
    }
}
