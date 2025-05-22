package com.angel.server.capture.controller;

import com.angel.server.capture.model.ActivityDetection;
import com.angel.server.capture.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour l'API de capture d'activité
 */
@RestController
@RequestMapping("${api.base.path:/api/v1}")
@CrossOrigin(origins = "*")
public class CaptureApiController {

    private static final Logger logger = LoggerFactory.getLogger(CaptureApiController.class);

    @Autowired
    private VideoCaptureService videoCaptureService;

    @Autowired
    private AudioCaptureService audioCaptureService;

    @Autowired
    private ActivityDetectionService activityDetectionService;

    @Autowired
    private PersonDetectionService personDetectionService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ModelService modelService;

    // ========== ENDPOINTS DE CONTRÔLE DE CAPTURE ==========

    /**
     * Démarre la capture d'activité
     */
    @PostMapping("/capture/start")
    public ResponseEntity<Map<String, Object>> startCapture() {
        try {
            logger.info("Demande de démarrage de la capture via API");
            
            // Démarrer la capture vidéo
            videoCaptureService.startCapture();
            
            // Démarrer la capture audio
            audioCaptureService.startCapture();
            
            // Démarrer la détection d'activité
            activityDetectionService.startDetection();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Capture démarrée avec succès");
            response.put("timestamp", System.currentTimeMillis());
            response.put("video_capturing", videoCaptureService.isCapturing());
            response.put("audio_capturing", audioCaptureService.isCapturing());
            response.put("activity_detecting", activityDetectionService.isDetecting());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors du démarrage de la capture: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors du démarrage: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Arrête la capture d'activité
     */
    @PostMapping("/capture/stop")
    public ResponseEntity<Map<String, Object>> stopCapture() {
        try {
            logger.info("Demande d'arrêt de la capture via API");
            
            // Arrêter la détection d'activité
            activityDetectionService.stopDetection();
            
            // Arrêter la capture audio
            audioCaptureService.stopCapture();
            
            // Arrêter la capture vidéo
            videoCaptureService.stopCapture();
            
            // Forcer la sauvegarde de l'historique
            historyService.forceSave();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Capture arrêtée avec succès");
            response.put("timestamp", System.currentTimeMillis());
            response.put("video_capturing", videoCaptureService.isCapturing());
            response.put("audio_capturing", audioCaptureService.isCapturing());
            response.put("activity_detecting", activityDetectionService.isDetecting());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors de l'arrêt de la capture: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors de l'arrêt: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Retourne le statut de la capture
     */
    @GetMapping("/capture/status")
    public ResponseEntity<Map<String, Object>> getCaptureStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("video_capturing", videoCaptureService.isCapturing());
        status.put("audio_capturing", audioCaptureService.isCapturing());
        status.put("activity_detecting", activityDetectionService.isDetecting());
        status.put("video_sources", videoCaptureService.getActiveSources());
        status.put("video_sources_count", videoCaptureService.getActiveSourcesCount());
        status.put("person_detection_enabled", personDetectionService.isEnabled());
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }

    // ========== ENDPOINTS D'ACTIVITÉ COURANTE ==========

    /**
     * Retourne l'activité courante détectée
     */
    @GetMapping("/activity/current")
    public ResponseEntity<Map<String, Object>> getCurrentActivity() {
        Map<String, Object> response = new HashMap<>();
        
        ActivityDetection lastDetection = activityDetectionService.getLastDetection();
        if (lastDetection != null) {
            response.put("status", "success");
            response.put("activity", lastDetection);
            response.put("has_detection", true);
        } else {
            response.put("status", "success");
            response.put("activity", null);
            response.put("has_detection", false);
            response.put("message", "Aucune activité détectée récemment");
        }
        
        response.put("timestamp", System.currentTimeMillis());
        response.put("is_detecting", activityDetectionService.isDetecting());
        
        return ResponseEntity.ok(response);
    }

    // ========== ENDPOINTS D'HISTORIQUE ==========

    /**
     * Retourne l'historique du jour
     */
    @GetMapping("/history/today")
    public ResponseEntity<Map<String, Object>> getTodayHistory() {
        try {
            List<ActivityDetection> history = historyService.getTodayHistory();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("date", LocalDate.now().toString());
            response.put("detections", history);
            response.put("count", history.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'historique du jour: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors de la récupération: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Retourne l'historique de la semaine
     */
    @GetMapping("/history/week")
    public ResponseEntity<Map<String, Object>> getWeekHistory() {
        try {
            List<ActivityDetection> history = historyService.getWeekHistory();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("period", "week");
            response.put("end_date", LocalDate.now().toString());
            response.put("start_date", LocalDate.now().minusDays(6).toString());
            response.put("detections", history);
            response.put("count", history.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'historique de la semaine: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors de la récupération: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Retourne l'historique du mois
     */
    @GetMapping("/history/month")
    public ResponseEntity<Map<String, Object>> getMonthHistory() {
        try {
            List<ActivityDetection> history = historyService.getMonthHistory();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("period", "month");
            response.put("end_date", LocalDate.now().toString());
            response.put("start_date", LocalDate.now().minusDays(29).toString());
            response.put("detections", history);
            response.put("count", history.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'historique du mois: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors de la récupération: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Retourne l'historique pour une date spécifique
     */
    @GetMapping("/history/date/{date}")
    public ResponseEntity<Map<String, Object>> getHistoryForDate(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            List<ActivityDetection> history = historyService.getHistoryForDate(date.toString());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("date", date.toString());
            response.put("detections", history);
            response.put("count", history.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'historique pour {}: {}", date, e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors de la récupération: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Retourne l'historique pour une période
     */
    @GetMapping("/history/period")
    public ResponseEntity<Map<String, Object>> getHistoryForPeriod(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        try {
            List<ActivityDetection> history = historyService.getHistoryForPeriod(startDate, endDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("start_date", startDate.toString());
            response.put("end_date", endDate.toString());
            response.put("detections", history);
            response.put("count", history.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'historique pour la période {}-{}: {}", 
                        startDate, endDate, e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors de la récupération: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Supprime l'historique à partir d'une date
     */
    @DeleteMapping("/history/from/{date}")
    public ResponseEntity<Map<String, Object>> deleteHistoryFromDate(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            boolean success = historyService.deleteHistoryFromDate(date);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("status", "success");
                response.put("message", "Historique supprimé à partir du " + date.toString());
            } else {
                response.put("status", "partial_success");
                response.put("message", "Historique partiellement supprimé à partir du " + date.toString());
            }
            response.put("from_date", date.toString());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors de la suppression de l'historique depuis {}: {}", date, e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors de la suppression: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ========== ENDPOINTS DE GESTION DES PERSONNES ==========

    /**
     * Ajoute une image de référence pour la détection de personne
     */
    @PostMapping("/person/reference")
    public ResponseEntity<Map<String, Object>> addReferenceImage(
            @RequestParam("name") String personName,
            @RequestParam("image") MultipartFile imageFile) {
        try {
            if (imageFile.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Fichier image vide");
                return ResponseEntity.badRequest().body(response);
            }

            BufferedImage image = ImageIO.read(imageFile.getInputStream());
            if (image == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Format d'image non supporté");
                return ResponseEntity.badRequest().body(response);
            }

            boolean success = personDetectionService.addReferenceImage(personName, image);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("status", "success");
                response.put("message", "Image de référence ajoutée pour " + personName);
            } else {
                response.put("status", "error");
                response.put("message", "Erreur lors de l'ajout de l'image de référence");
            }
            response.put("person_name", personName);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            logger.error("Erreur lors de l'ajout de l'image de référence: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors du traitement de l'image: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Supprime une image de référence
     */
    @DeleteMapping("/person/reference/{name}")
    public ResponseEntity<Map<String, Object>> removeReferenceImage(@PathVariable String name) {
        try {
            boolean success = personDetectionService.removeReferenceImage(name);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("status", "success");
                response.put("message", "Image de référence supprimée pour " + name);
            } else {
                response.put("status", "error");
                response.put("message", "Erreur lors de la suppression de l'image de référence");
            }
            response.put("person_name", name);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erreur lors de la suppression de l'image de référence: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erreur lors de la suppression: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Liste les personnes de référence
     */
    @GetMapping("/person/reference")
    public ResponseEntity<Map<String, Object>> listReferencePersons() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("persons", personDetectionService.getReferencePersons());
        response.put("count", personDetectionService.getReferencePersons().size());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    // ========== ENDPOINTS DE STATISTIQUES ==========

    /**
     * Retourne les statistiques générales
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("capture_status", getCaptureStatus().getBody());
        stats.put("detection_stats", activityDetectionService.getDetectionStats());
        stats.put("person_detection_stats", personDetectionService.getDetectionStats());
        stats.put("history_stats", historyService.getHistoryStats());
        stats.put("model_stats", modelService.getModelStats());
        stats.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(stats);
    }

    // ========== ENDPOINT DE SANTÉ ==========

    /**
     * Point de santé de l'application
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("version", "1.0-SNAPSHOT");
        
        return ResponseEntity.ok(health);
    }
}