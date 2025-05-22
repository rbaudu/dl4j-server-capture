package com.angel.server.capture.config;

import com.angel.server.capture.model.ActivityDetection;
import com.angel.server.capture.service.ActivityDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire WebSocket pour diffuser les détections d'activité en temps réel
 */
@Component
public class ActivityWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ActivityWebSocketHandler.class);

    @Autowired
    private ActivityDetectionService activityDetectionService;

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initialize() {
        // S'abonner aux détections d'activité
        activityDetectionService.addDetectionListener(this::broadcastDetection);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        logger.info("Nouvelle connexion WebSocket pour les activités: {}", session.getId());

        // Envoyer la dernière détection si disponible
        ActivityDetection lastDetection = activityDetectionService.getLastDetection();
        if (lastDetection != null) {
            sendDetection(session, lastDetection);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // Gérer les messages du client si nécessaire
        logger.debug("Message reçu du client {}: {}", session.getId(), message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Erreur de transport WebSocket pour {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        sessions.remove(session);
        logger.info("Connexion WebSocket fermée pour les activités: {} ({})", 
                   session.getId(), closeStatus.toString());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Diffuse une détection à tous les clients connectés
     */
    private void broadcastDetection(ActivityDetection detection) {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            String message = objectMapper.writeValueAsString(detection);
            TextMessage textMessage = new TextMessage(message);

            sessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                        return false;
                    } else {
                        return true; // Supprimer les sessions fermées
                    }
                } catch (Exception e) {
                    logger.error("Erreur lors de l'envoi à la session {}: {}", session.getId(), e.getMessage());
                    return true; // Supprimer les sessions en erreur
                }
            });

        } catch (Exception e) {
            logger.error("Erreur lors de la diffusion de la détection: {}", e.getMessage());
        }
    }

    /**
     * Envoie une détection à une session spécifique
     */
    private void sendDetection(WebSocketSession session, ActivityDetection detection) {
        try {
            if (session.isOpen()) {
                String message = objectMapper.writeValueAsString(detection);
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            logger.error("Erreur lors de l'envoi de la détection à {}: {}", session.getId(), e.getMessage());
        }
    }
}