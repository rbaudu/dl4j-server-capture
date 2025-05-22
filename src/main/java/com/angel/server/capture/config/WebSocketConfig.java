package com.angel.server.capture.config;

import com.angel.server.capture.service.VideoCaptureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new VideoWebSocketHandler(), "/ws/video")
                .setAllowedOrigins("*");
    }
    
    @Component
    public static class VideoWebSocketHandler extends TextWebSocketHandler {
        
        private static final Logger logger = LoggerFactory.getLogger(VideoWebSocketHandler.class);
        
        @Autowired
        private VideoCaptureService videoCaptureService;
        
        private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            try {
                sessions.add(session);
                logger.info("Nouvelle connexion WebSocket établie: {}", session.getId());
                
                // Créer un listener pour ce client
                Consumer<BufferedImage> frameListener = frame -> {
                    try {
                        sendFrameToClient(session, frame);
                    } catch (Exception e) {
                        logger.debug("Erreur lors de l'envoi de frame à {}: {}", 
                                   session.getId(), e.getMessage());
                        sessions.remove(session);
                    }
                };
                
                // Stocker le listener dans les attributs de session
                session.getAttributes().put("frameListener", frameListener);
                videoCaptureService.addFrameListener(frameListener);
                
                // Envoyer un message de bienvenue
                session.sendMessage(new TextMessage("{\"type\":\"connected\",\"message\":\"WebSocket connecté\"}"));
                
            } catch (Exception e) {
                logger.error("Erreur lors de l'établissement de la connexion WebSocket: {}", e.getMessage());
                sessions.remove(session);
            }
        }
        
        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            sessions.remove(session);
            logger.info("Connexion WebSocket fermée: {} - Status: {}", session.getId(), status);
            
            // Supprimer le listener
            @SuppressWarnings("unchecked")
            Consumer<BufferedImage> frameListener = 
                (Consumer<BufferedImage>) session.getAttributes().get("frameListener");
            if (frameListener != null) {
                videoCaptureService.removeFrameListener(frameListener);
            }
        }
        
        @Override
        public void handleTextMessage(WebSocketSession session, TextMessage message) {
            try {
                String payload = message.getPayload();
                logger.debug("Message reçu de {}: {}", session.getId(), payload);
                
                // Gérer les messages de contrôle
                if ("ping".equals(payload)) {
                    session.sendMessage(new TextMessage("pong"));
                } else if ("start_capture".equals(payload)) {
                    if (!videoCaptureService.isCapturing()) {
                        videoCaptureService.startCapture();
                    }
                    session.sendMessage(new TextMessage("{\"type\":\"status\",\"capturing\":true}"));
                } else if ("stop_capture".equals(payload)) {
                    if (videoCaptureService.isCapturing()) {
                        videoCaptureService.stopCapture();
                    }
                    session.sendMessage(new TextMessage("{\"type\":\"status\",\"capturing\":false}"));
                }
                
            } catch (Exception e) {
                logger.error("Erreur lors du traitement du message WebSocket: {}", e.getMessage());
            }
        }
        
        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            logger.error("Erreur de transport WebSocket pour {}: {}", session.getId(), exception.getMessage());
            sessions.remove(session);
            
            // Nettoyer le listener
            @SuppressWarnings("unchecked")
            Consumer<BufferedImage> frameListener = 
                (Consumer<BufferedImage>) session.getAttributes().get("frameListener");
            if (frameListener != null) {
                videoCaptureService.removeFrameListener(frameListener);
            }
        }
        
        /**
         * Envoie une frame au client via WebSocket
         */
        private void sendFrameToClient(WebSocketSession session, BufferedImage frame) throws Exception {
            if (session.isOpen()) {
                // Convertir l'image en JPEG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if (!ImageIO.write(frame, "jpg", baos)) {
                    throw new IOException("Impossible d'encoder l'image en JPEG");
                }
                byte[] imageBytes = baos.toByteArray();
                baos.close();
                
                // Encoder en base64
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                
                // Créer le message JSON
                String jsonMessage = String.format(
                    "{\"type\":\"frame\",\"data\":\"data:image/jpeg;base64,%s\",\"timestamp\":%d}",
                    base64Image, System.currentTimeMillis()
                );
                
                // Envoyer via WebSocket
                session.sendMessage(new TextMessage(jsonMessage));
            }
        }
        
        /**
         * Diffuse un message à tous les clients connectés
         */
        public void broadcastMessage(String message) {
            sessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(message));
                        return false; // Garder la session
                    } else {
                        return true; // Supprimer la session fermée
                    }
                } catch (Exception e) {
                    logger.debug("Erreur lors de la diffusion à {}: {}", session.getId(), e.getMessage());
                    return true; // Supprimer la session en erreur
                }
            });
        }
        
        /**
         * Retourne le nombre de clients connectés
         */
        public int getConnectedClientsCount() {
            return sessions.size();
        }
        
        /**
         * Ferme toutes les connexions
         */
        public void closeAllConnections() {
            sessions.forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (Exception e) {
                    logger.debug("Erreur lors de la fermeture de session {}: {}", 
                               session.getId(), e.getMessage());
                }
            });
            sessions.clear();
        }
    }
}