package com.angel.server.capture.service;

import com.angel.server.capture.model.ActivityDetection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Service principal orchestrant tous les autres services
 * Point d'entrée pour le contrôle global de l'application
 */
@Service
public class CaptureOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(CaptureOrchestrationService.class);

    @Autowired
    private VideoCaptureService videoCaptureService;

    @Autowired
    private AudioCaptureService audioCaptureService;

    @Autowired
    private ActivityDetectionService activityDetectionService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ModelService modelService;

    @PostConstruct
    public void initialize() {
        logger.info("Initialisation du service d'orchestration de capture...");
        
        // Configurer les listeners pour l'historique
        activityDetectionService.addDetectionListener(this::onActivityDetected);
        
        logger.info("Service d'orchestration initialisé");
    }

    /**
     * Démarre tous les services de capture
     */
    public void startAll() {
        logger.info("Démarrage de tous les services de capture...");
        
        try {
            // Démarrer dans l'ordre
            videoCaptureService.startCapture();
            audioCaptureService.startCapture();
            activityDetectionService.startDetection();
            
            logger.info("Tous les services de capture ont été démarrés");
            
        } catch (Exception e) {
            logger.error("Erreur lors du démarrage des services: {}", e.getMessage());
            // En cas d'erreur, arrêter ce qui a pu démarrer
            stopAll();
            throw new RuntimeException("Échec du démarrage des services", e);
        }
    }

    /**
     * Arrête tous les services de capture
     */
    public void stopAll() {
        logger.info("Arrêt de tous les services de capture...");
        
        try {
            // Arrêter dans l'ordre inverse
            activityDetectionService.stopDetection();
            audioCaptureService.stopCapture();
            videoCaptureService.stopCapture();
            
            // Forcer la sauvegarde de l'historique
            historyService.forceSave();
            
            logger.info("Tous les services de capture ont été arrêtés");
            
        } catch (Exception e) {
            logger.error("Erreur lors de l'arrêt des services: {}", e.getMessage());
        }
    }

    /**
     * Vérifie l'état de santé de tous les services
     */
    public boolean isHealthy() {
        try {
            // Vérifier que les modèles sont disponibles
            if (modelService.getModelStats().get("facenet_loaded").equals(false)) {
                logger.warn("Modèle FaceNet non chargé");
            }
            
            // Autres vérifications de santé peuvent être ajoutées ici
            return true;
            
        } catch (Exception e) {
            logger.error("Erreur lors de la vérification de santé: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Callback appelé lors de chaque détection d'activité
     */
    private void onActivityDetected(ActivityDetection detection) {
        try {
            // Ajouter à l'historique
            historyService.addDetection(detection);
            
            logger.debug("Activité ajoutée à l'historique: {} (confiance: {:.2f})", 
                        detection.getPredictedActivity(), detection.getConfidence());
            
        } catch (Exception e) {
            logger.error("Erreur lors de l'ajout à l'historique: {}", e.getMessage());
        }
    }

    /**
     * Redémarre tous les services
     */
    public void restart() {
        logger.info("Redémarrage de tous les services...");
        stopAll();
        
        // Attendre un peu avant de redémarrer
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        startAll();
    }

    /**
     * Nettoyage à la fermeture de l'application
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Nettoyage final de l'orchestrateur...");
        stopAll();
    }
}