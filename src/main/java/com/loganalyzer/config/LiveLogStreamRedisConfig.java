package com.loganalyzer.config;

import com.loganalyzer.service.LiveLogStreamFanoutService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class LiveLogStreamRedisConfig {

    private final LiveLogStreamFanoutService fanoutService;

    @Bean
    RedisMessageListenerContainer liveLogRedisListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
            (message, pattern) -> fanoutService.onRedisMessage(new String(message.getBody(), StandardCharsets.UTF_8)),
            new ChannelTopic(LiveLogStreamFanoutService.REDIS_CHANNEL)
        );
        return container;
    }
}
