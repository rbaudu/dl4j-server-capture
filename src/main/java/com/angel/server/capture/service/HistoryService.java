package com.angel.server.capture.service;

import com.angel.server.capture.model.ActivityDetection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Service de gestion de l'historique des détections d'activité
 * Sauvegarde simple basée sur des fichiers JSON
 */
@Service
public class HistoryService {

    private static final Logger logger = LoggerFactory.getLogger(HistoryService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // Configuration depuis application.properties
    @Value("${history.directory}")
    private String historyDirectory;

    @Value("${history.file.format}")
    private String fileFormat;

    @Value("${history.rotation.daily}")
    private boolean dailyRotation;

    @Value("${history.retention.days}")
    private int retentionDays;

    @Value("${history.auto.save.interval}")
    private int autoSaveInterval;

    // Composants
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Buffer des détections en mémoire
    private final Queue<ActivityDetection> detectionBuffer = new ConcurrentLinkedQueue<>();
    
    // Cache des historiques chargés
    private final Map<String, List<ActivityDetection>> historyCache = new HashMap<>();

    @PostConstruct
    public void initialize() {
        logger.info("Initialisation du service d'historique...");
        
        // Configurer ObjectMapper pour les dates
        objectMapper.registerModule(new JavaTimeModule());
        
        // Créer le répertoire d'historique s'il n'existe pas
        File historyDir = new File(historyDirectory);
        if (!historyDir.exists()) {
            historyDir.mkdirs();
            logger.info("Répertoire d'historique créé: {}", historyDirectory);
        }
        
        // Nettoyer les anciens fichiers
        cleanupOldFiles();
        
        logger.info("Service d'historique initialisé");
    }

    /**
     * Ajoute une détection à l'historique
     */
    public void addDetection(ActivityDetection detection) {
        if (detection == null) {
            return;
        }

        // Ajouter au buffer
        detectionBuffer.offer(detection);
        
        // Limiter la taille du buffer
        while (detectionBuffer.size() > 1000) {
            detectionBuffer.poll();
        }

        logger.debug("Détection ajoutée à l'historique: {}", detection.getPredictedActivity());
    }

    /**
     * Sauvegarde automatique des détections en attente
     */
    @Scheduled(fixedRateString = "${history.auto.save.interval}000") // Convertir en millisecondes
    public void autoSave() {
        if (detectionBuffer.isEmpty()) {
            return;
        }

        logger.debug("Sauvegarde automatique de {} détections", detectionBuffer.size());
        
        List<ActivityDetection> detectionsToSave = new ArrayList<>();
        ActivityDetection detection;
        while ((detection = detectionBuffer.poll()) != null) {
            detectionsToSave.add(detection);
        }

        if (!detectionsToSave.isEmpty()) {
            saveDetections(detectionsToSave);
        }
    }

    /**
     * Sauvegarde une liste de détections
     */
    private void saveDetections(List<ActivityDetection> detections) {
        lock.writeLock().lock();
        try {
            // Grouper les détections par date
            Map<String, List<ActivityDetection>> detectionsByDate = detections.stream()
                .collect(Collectors.groupingBy(d -> d.getTimestamp().toLocalDate().format(DATE_FORMATTER)));

            for (Map.Entry<String, List<ActivityDetection>> entry : detectionsByDate.entrySet()) {
                String date = entry.getKey();
                List<ActivityDetection> dailyDetections = entry.getValue();
                
                // Charger les détections existantes
                List<ActivityDetection> existingDetections = loadDetectionsForDate(date);
                existingDetections.addAll(dailyDetections);
                
                // Trier par timestamp
                existingDetections.sort(Comparator.comparing(ActivityDetection::getTimestamp));
                
                // Sauvegarder
                saveDetectionsForDate(date, existingDetections);
                
                // Mettre à jour le cache
                historyCache.put(date, existingDetections);
            }
            
        } catch (Exception e) {
            logger.error("Erreur lors de la sauvegarde des détections: {}", e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Charge les détections pour une date donnée
     */
    private List<ActivityDetection> loadDetectionsForDate(String date) {
        // Vérifier le cache
        if (historyCache.containsKey(date)) {
            return new ArrayList<>(historyCache.get(date));
        }

        File file = getFileForDate(date);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try {
            ActivityDetection[] detections = objectMapper.readValue(file, ActivityDetection[].class);
            List<ActivityDetection> detectionList = Arrays.asList(detections);
            
            // Mettre en cache
            historyCache.put(date, new ArrayList<>(detectionList));
            
            return new ArrayList<>(detectionList);
            
        } catch (IOException e) {
            logger.error("Erreur lors du chargement des détections pour {}: {}", date, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Sauvegarde les détections pour une date donnée
     */
    private void saveDetectionsForDate(String date, List<ActivityDetection> detections) {
        File file = getFileForDate(date);
        
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, detections);
            logger.debug("Détections sauvegardées pour {}: {} éléments", date, detections.size());
            
        } catch (IOException e) {
            logger.error("Erreur lors de la sauvegarde des détections pour {}: {}", date, e.getMessage());
        }
    }

    /**
     * Retourne le fichier pour une date donnée
     */
    private File getFileForDate(String date) {
        String filename = String.format("detections_%s.%s", date, fileFormat);
        return new File(historyDirectory, filename);
    }

    /**
     * Retourne l'historique du jour
     */
    public List<ActivityDetection> getTodayHistory() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        return getHistoryForDate(today);
    }

    /**
     * Retourne l'historique pour une date donnée
     */
    public List<ActivityDetection> getHistoryForDate(String date) {
        lock.readLock().lock();
        try {
            return loadDetectionsForDate(date);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retourne l'historique pour une période
     */
    public List<ActivityDetection> getHistoryForPeriod(LocalDate startDate, LocalDate endDate) {
        lock.readLock().lock();
        try {
            List<ActivityDetection> allDetections = new ArrayList<>();
            
            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                String dateStr = currentDate.format(DATE_FORMATTER);
                allDetections.addAll(loadDetectionsForDate(dateStr));
                currentDate = currentDate.plusDays(1);
            }
            
            // Trier par timestamp
            allDetections.sort(Comparator.comparing(ActivityDetection::getTimestamp));
            
            return allDetections;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retourne l'historique de la semaine
     */
    public List<ActivityDetection> getWeekHistory() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6);
        return getHistoryForPeriod(startDate, endDate);
    }

    /**
     * Retourne l'historique du mois
     */
    public List<ActivityDetection> getMonthHistory() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);
        return getHistoryForPeriod(startDate, endDate);
    }

    /**
     * Supprime l'historique à partir d'une date
     */
    public boolean deleteHistoryFromDate(LocalDate fromDate) {
        lock.writeLock().lock();
        try {
            boolean success = true;
            File historyDir = new File(historyDirectory);
            File[] files = historyDir.listFiles((dir, name) -> name.startsWith("detections_") && name.endsWith("." + fileFormat));
            
            if (files != null) {
                for (File file : files) {
                    try {
                        String filename = file.getName();
                        String dateStr = filename.substring("detections_".length(), filename.lastIndexOf('.'));
                        LocalDate fileDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                        
                        if (!fileDate.isBefore(fromDate)) {
                            if (file.delete()) {
                                historyCache.remove(dateStr);
                                logger.info("Fichier d'historique supprimé: {}", filename);
                            } else {
                                success = false;
                                logger.warn("Impossible de supprimer le fichier: {}", filename);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Erreur lors de la suppression du fichier {}: {}", file.getName(), e.getMessage());
                        success = false;
                    }
                }
            }
            
            return success;
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Nettoie les anciens fichiers selon la politique de rétention
     */
    @Scheduled(cron = "0 0 2 * * ?") // Tous les jours à 2h du matin
    public void cleanupOldFiles() {
        if (retentionDays <= 0) {
            return;
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
        logger.info("Nettoyage des fichiers d'historique antérieurs au {}", cutoffDate.format(DATE_FORMATTER));
        
        lock.writeLock().lock();
        try {
            File historyDir = new File(historyDirectory);
            File[] files = historyDir.listFiles((dir, name) -> name.startsWith("detections_") && name.endsWith("." + fileFormat));
            
            if (files != null) {
                int deletedCount = 0;
                for (File file : files) {
                    try {
                        String filename = file.getName();
                        String dateStr = filename.substring("detections_".length(), filename.lastIndexOf('.'));
                        LocalDate fileDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                        
                        if (fileDate.isBefore(cutoffDate)) {
                            if (file.delete()) {
                                historyCache.remove(dateStr);
                                deletedCount++;
                                logger.debug("Ancien fichier d'historique supprimé: {}", filename);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Erreur lors du nettoyage du fichier {}: {}", file.getName(), e.getMessage());
                    }
                }
                
                if (deletedCount > 0) {
                    logger.info("{} anciens fichiers d'historique supprimés", deletedCount);
                }
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retourne les statistiques de l'historique
     */
    public Map<String, Object> getHistoryStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Compter les détections en buffer
            stats.put("buffered_detections", detectionBuffer.size());
            
            // Compter les fichiers d'historique
            File historyDir = new File(historyDirectory);
            File[] files = historyDir.listFiles((dir, name) -> name.startsWith("detections_") && name.endsWith("." + fileFormat));
            stats.put("history_files_count", files != null ? files.length : 0);
            
            // Statistiques du cache
            stats.put("cached_dates", historyCache.size());
            
            // Taille totale des fichiers
            long totalSize = 0;
            if (files != null) {
                for (File file : files) {
                    totalSize += file.length();
                }
            }
            stats.put("total_size_bytes", totalSize);
            stats.put("total_size_mb", totalSize / (1024.0 * 1024.0));
            
            // Configuration
            stats.put("retention_days", retentionDays);
            stats.put("auto_save_interval_seconds", autoSaveInterval);
            stats.put("daily_rotation", dailyRotation);
            
            return stats;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Exporte l'historique vers un fichier
     */
    public boolean exportHistory(LocalDate startDate, LocalDate endDate, File outputFile) {
        try {
            List<ActivityDetection> history = getHistoryForPeriod(startDate, endDate);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, history);
            
            logger.info("Historique exporté vers {}: {} détections", outputFile.getAbsolutePath(), history.size());
            return true;
            
        } catch (Exception e) {
            logger.error("Erreur lors de l'export de l'historique: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Importe l'historique depuis un fichier
     */
    public boolean importHistory(File inputFile) {
        try {
            ActivityDetection[] detections = objectMapper.readValue(inputFile, ActivityDetection[].class);
            List<ActivityDetection> detectionList = Arrays.asList(detections);
            
            saveDetections(detectionList);
            
            logger.info("Historique importé depuis {}: {} détections", inputFile.getAbsolutePath(), detectionList.size());
            return true;
            
        } catch (Exception e) {
            logger.error("Erreur lors de l'import de l'historique: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Force la sauvegarde de toutes les détections en attente
     */
    public void forceSave() {
        autoSave();
    }

    /**
     * Vide le cache en mémoire
     */
    public void clearCache() {
        lock.writeLock().lock();
        try {
            historyCache.clear();
            logger.info("Cache d'historique vidé");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Nettoyage automatique à la fermeture
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Sauvegarde finale de l'historique...");
        forceSave();
    }
}