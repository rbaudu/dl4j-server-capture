package com.angel.server.capture.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuration WebSocket pour le streaming temps réel
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${websocket.endpoint:/ws}")
    private String websocketEndpoint;

    @Value("${websocket.allowed.origins:*}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ActivityWebSocketHandler(), websocketEndpoint)
                .setAllowedOrigins(allowedOrigins.split(","));
        
        registry.addHandler(new VideoStreamWebSocketHandler(), "/ws/video")
                .setAllowedOrigins(allowedOrigins.split(","));
    }
}