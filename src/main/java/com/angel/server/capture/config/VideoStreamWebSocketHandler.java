package com.angel.server.capture.config;

import com.angel.server.capture.service.VideoCaptureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gestionnaire WebSocket pour diffuser le flux vidéo en temps réel
 */
@Component
public class VideoStreamWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(VideoStreamWebSocketHandler.class);

    @Autowired
    private VideoCaptureService videoCaptureService;

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService streamExecutor;
    private volatile BufferedImage lastFrame;

    @PostConstruct
    public void initialize() {
        // S'abonner aux frames vidéo
        videoCaptureService.addFrameListener(this::updateLastFrame);
        
        // Démarrer le streaming à 10 FPS
        streamExecutor = Executors.newSingleThreadScheduledExecutor();
        streamExecutor.scheduleWithFixedDelay(this::streamFrame, 0, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        logger.info("Nouvelle connexion WebSocket pour le streaming vidéo: {}", session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // Gérer les messages du client si nécessaire
        logger.debug("Message reçu du client vidéo {}: {}", session.getId(), message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Erreur de transport WebSocket vidéo pour {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        sessions.remove(session);
        logger.info("Connexion WebSocket vidéo fermée: {} ({})", 
                   session.getId(), closeStatus.toString());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Met à jour la dernière frame reçue
     */
    private void updateLastFrame(BufferedImage frame) {
        this.lastFrame = frame;
    }

    /**
     * Diffuse la frame courante à tous les clients connectés
     */
    private void streamFrame() {
        if (sessions.isEmpty() || lastFrame == null) {
            return;
        }

        try {
            // Convertir l'image en JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(lastFrame, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            
            BinaryMessage binaryMessage = new BinaryMessage(imageBytes);

            sessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(binaryMessage);
                        return false;
                    } else {
                        return true; // Supprimer les sessions fermées
                    }
                } catch (Exception e) {
                    logger.error("Erreur lors de l'envoi vidéo à la session {}: {}", session.getId(), e.getMessage());
                    return true; // Supprimer les sessions en erreur
                }
            });

        } catch (IOException e) {
            logger.error("Erreur lors de l'encodage de la frame: {}", e.getMessage());
        }
    }
}