package com.angel.server.capture.service;

import com.angel.server.capture.model.ActivityClass;
import com.angel.server.capture.model.ActivityDetection;
import com.angel.server.capture.model.DetectionSource;
import com.angel.server.capture.model.FusionWeights;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service principal de détection d'activité
 * Combine les détections d'images et de sons pour déterminer l'activité en cours
 * Ne fait la détection que si une personne est présente
 */
@Service
public class ActivityDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityDetectionService.class);

    @Autowired
    private ModelService modelService;

    @Autowired
    private VideoCaptureService videoCaptureService;

    @Autowired
    private AudioCaptureService audioCaptureService;

    @Autowired
    private PersonDetectionService personDetectionService;

    @Autowired
    private PresenceDetectionService presenceDetectionService;

    // Configuration depuis application.properties
    @Value("${detection.interval}")
    private int detectionInterval;

    @Value("${detection.image.width}")
    private int imageWidth;

    @Value("${detection.image.height}")
    private int imageHeight;

    @Value("${detection.confidence.threshold}")
    private double confidenceThreshold;

    @Value("${detection.fusion.enabled}")
    private boolean fusionEnabled;

    @Value("${detection.fusion.image.weight}")
    private double imageWeight;

    @Value("${detection.fusion.sound.weight}")
    private double soundWeight;

    @Value("${threads.detection.pool.size}")
    private int detectionThreadPoolSize;

    @Value("${cache.predictions.size}")
    private int predictionCacheSize;

    @Value("${cache.predictions.ttl}")
    private int predictionCacheTTL;

    @Value("${detection.require.person.presence:true}")
    private boolean requirePersonPresence;

    @Value("${person.detection.type}")
    private String personDetectionType;

    // État du service
    private volatile boolean isDetecting = false;
    private ScheduledExecutorService detectionExecutor;
    
    // Buffers pour les données
    private final Queue<BufferedImage> imageBuffer = new ConcurrentLinkedQueue<>();
    private final Queue<byte[]> audioBuffer = new ConcurrentLinkedQueue<>();
    
    // Cache des prédictions récentes
    private final Map<String, PredictionCacheEntry> predictionCache = new ConcurrentHashMap<>();
    
    // Listeners pour les détections
    private final List<Consumer<ActivityDetection>> detectionListeners = new ArrayList<>();
    
    // Dernière détection
    private volatile ActivityDetection lastDetection;
    
    @PostConstruct
    public void initialize() {
        logger.info("Initialisation du service de détection d'activité...");
        
        // S'abonner aux flux vidéo et audio
        videoCaptureService.addFrameListener(this::onFrameReceived);
        audioCaptureService.addAudioListener(this::onAudioReceived);
        
        logger.info("Détection d'activité configurée - Nécessite présence: {}, Type de détection: {}", 
                   requirePersonPresence, personDetectionType);
        logger.info("Service de détection d'activité initialisé");
    }

    /**
     * Démarre la détection d'activité
     */
    public synchronized void startDetection() {
        if (isDetecting) {
            logger.warn("La détection d'activité est déjà en cours");
            return;
        }

        logger.info("Démarrage de la détection d'activité...");

        // Créer le pool de threads pour la détection
        detectionExecutor = Executors.newScheduledThreadPool(detectionThreadPoolSize);

        // Planifier la détection périodique
        detectionExecutor.scheduleWithFixedDelay(
            this::performDetection,
            0, detectionInterval, TimeUnit.MILLISECONDS
        );

        // Planifier le nettoyage du cache
        detectionExecutor.scheduleWithFixedDelay(
            this::cleanPredictionCache,
            predictionCacheTTL, predictionCacheTTL, TimeUnit.SECONDS
        );

        isDetecting = true;
        logger.info("Détection d'activité démarrée");
    }

    /**
     * Arrête la détection d'activité
     */
    public synchronized void stopDetection() {
        if (!isDetecting) {
            logger.warn("La détection d'activité n'est pas en cours");
            return;
        }

        logger.info("Arrêt de la détection d'activité...");

        isDetecting = false;

        // Arrêter le pool de threads
        if (detectionExecutor != null) {
            detectionExecutor.shutdown();
            try {
                if (!detectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    detectionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                detectionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Vider les buffers
        imageBuffer.clear();
        audioBuffer.clear();

        logger.info("Détection d'activité arrêtée");
    }

    /**
     * Callback pour les frames vidéo reçues
     */
    private void onFrameReceived(BufferedImage frame) {
        if (!isDetecting) {
            return;
        }

        // Ajouter au buffer avec limitation de taille
        imageBuffer.offer(frame);
        while (imageBuffer.size() > 10) { // Garder seulement les 10 dernières frames
            imageBuffer.poll();
        }
    }

    /**
     * Callback pour les données audio reçues
     */
    private void onAudioReceived(byte[] audioData) {
        if (!isDetecting) {
            return;
        }

        // Ajouter au buffer avec limitation de taille
        audioBuffer.offer(audioData);
        while (audioBuffer.size() > 5) { // Garder seulement les 5 derniers échantillons
            audioBuffer.poll();
        }
    }

    /**
     * Effectue la détection d'activité
     */
    private void performDetection() {
        try {
            logger.debug("Début du cycle de détection");

            // Vérifier s'il y a une personne détectée
            BufferedImage currentFrame = imageBuffer.peek();
            boolean personDetected = false;
            double personConfidence = 0.0;

            if (currentFrame != null && requirePersonPresence) {
                var personResult = detectPerson(currentFrame);
                personDetected = personResult.isPresent();
                if (personDetected) {
                    personConfidence = personResult.get();
                    logger.debug("Personne détectée avec confiance: {}", personConfidence);
                }
            } else if (!requirePersonPresence) {
                // Si la détection de personne n'est pas requise, continuer
                personDetected = true;
                personConfidence = 1.0;
            }

            // Si aucune personne n'est détectée et qu'elle est requise, pas de détection d'activité
            if (!personDetected && requirePersonPresence) {
                logger.debug("Aucune personne détectée, ignorer la détection d'activité");
                return;
            }

            // Effectuer la détection d'activité
            ActivityDetection detection = null;

            if (fusionEnabled) {
                // Fusion des prédictions image + son
                detection = performFusionDetection(personConfidence);
            } else {
                // Détection séparée (prioriser l'image)
                detection = performImageDetection(personConfidence);
                if (detection == null || detection.getConfidence() < confidenceThreshold) {
                    detection = performAudioDetection(personConfidence);
                }
            }

            // Vérifier la confiance minimale
            if (detection != null && detection.getConfidence() >= confidenceThreshold) {
                lastDetection = detection;
                notifyDetectionListeners(detection);
                logger.info("Activité détectée: {} (confiance: {:.2f})", 
                          detection.getPredictedActivity(), detection.getConfidence());
            } else {
                logger.debug("Confiance insuffisante pour la détection");
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la détection d'activité: {}", e.getMessage(), e);
        }
    }

    /**
     * Détecte la présence d'une personne selon la configuration
     */
    private Optional<Double> detectPerson(BufferedImage frame) {
        switch (personDetectionType.toLowerCase()) {
            case "presence":
                return presenceDetectionService.detectPresence(frame);
            case "facenet":
                return personDetectionService.detectPerson(frame);
            case "disabled":
            default:
                return Optional.empty();
        }
    }

    /**
     * Effectue la détection d'activité basée sur l'image
     */
    private ActivityDetection performImageDetection(double personConfidence) {
        BufferedImage frame = imageBuffer.poll();
        if (frame == null) {
            return null;
        }

        try {
            // Vérifier le cache
            String cacheKey = "image_" + System.currentTimeMillis() / 10000; // Cache pour 10 secondes
            PredictionCacheEntry cached = predictionCache.get(cacheKey);
            if (cached != null) {
                return createDetectionFromCache(cached, DetectionSource.CAMERA, personConfidence);
            }

            // Redimensionner l'image
            BufferedImage resized = resizeImage(frame, imageWidth, imageHeight);
            
            // Convertir en INDArray
            INDArray input = imageToINDArray(resized);
            
            // Obtenir le modèle d'activité par défaut
            MultiLayerNetwork model = modelService.getDefaultActivityImageModel();
            if (model == null) {
                logger.warn("Modèle d'activité image non disponible");
                return null;
            }

            // Faire la prédiction
            INDArray output = model.output(input);
            Map<String, Double> predictions = parsePredictions(output);
            
            // Trouver la meilleure prédiction
            String bestActivity = Collections.max(predictions.entrySet(), 
                Map.Entry.comparingByValue()).getKey();
            double confidence = predictions.get(bestActivity);

            // Mettre en cache
            predictionCache.put(cacheKey, new PredictionCacheEntry(predictions, System.currentTimeMillis()));

            // Créer la détection
            ActivityDetection detection = new ActivityDetection(bestActivity, confidence, DetectionSource.CAMERA);
            detection.setPersonDetected(true);
            detection.setPersonConfidence(personConfidence);
            detection.setPredictions(predictions);

            return detection;

        } catch (Exception e) {
            logger.error("Erreur lors de la détection d'activité par image: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Effectue la détection d'activité basée sur l'audio
     */
    private ActivityDetection performAudioDetection(double personConfidence) {
        byte[] audioData = audioBuffer.poll();
        if (audioData == null) {
            return null;
        }

        try {
            // Vérifier le cache
            String cacheKey = "audio_" + System.currentTimeMillis() / 10000; // Cache pour 10 secondes
            PredictionCacheEntry cached = predictionCache.get(cacheKey);
            if (cached != null) {
                return createDetectionFromCache(cached, DetectionSource.MICROPHONE, personConfidence);
            }

            // Obtenir le modèle de son par défaut
            MultiLayerNetwork model = modelService.getDefaultActivitySoundModel();
            if (model == null) {
                logger.warn("Modèle d'activité audio non disponible");
                return null;
            }

            // Convertir l'audio selon le type de modèle
            INDArray input;
            String defaultSoundModel = "spectrogram"; // Depuis la config
            
            if ("mfcc".equals(defaultSoundModel)) {
                float[] mfccFeatures = audioCaptureService.convertToMFCC(audioData);
                input = Nd4j.create(mfccFeatures).reshape(1, mfccFeatures.length);
            } else {
                // Spectrogramme par défaut
                float[][] spectrogram = audioCaptureService.convertToSpectrogram(audioData);
                input = Nd4j.create(spectrogram).reshape(1, spectrogram.length, spectrogram[0].length, 1);
            }

            // Faire la prédiction
            INDArray output = model.output(input);
            Map<String, Double> predictions = parsePredictions(output);
            
            // Trouver la meilleure prédiction
            String bestActivity = Collections.max(predictions.entrySet(), 
                Map.Entry.comparingByValue()).getKey();
            double confidence = predictions.get(bestActivity);

            // Mettre en cache
            predictionCache.put(cacheKey, new PredictionCacheEntry(predictions, System.currentTimeMillis()));

            // Créer la détection
            ActivityDetection detection = new ActivityDetection(bestActivity, confidence, DetectionSource.MICROPHONE);
            detection.setPersonDetected(true);
            detection.setPersonConfidence(personConfidence);
            detection.setPredictions(predictions);

            return detection;

        } catch (Exception e) {
            logger.error("Erreur lors de la détection d'activité par audio: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Effectue la détection par fusion des modalités image + son
     */
    private ActivityDetection performFusionDetection(double personConfidence) {
        try {
            // Obtenir les prédictions image et audio
            ActivityDetection imageDetection = performImageDetection(personConfidence);
            ActivityDetection audioDetection = performAudioDetection(personConfidence);

            if (imageDetection == null && audioDetection == null) {
                return null;
            }

            Map<String, Double> fusedPredictions = new HashMap<>();

            // Fusionner les prédictions
            if (imageDetection != null && audioDetection != null) {
                // Fusion pondérée
                Set<String> allActivities = new HashSet<>();
                allActivities.addAll(imageDetection.getPredictions().keySet());
                allActivities.addAll(audioDetection.getPredictions().keySet());

                for (String activity : allActivities) {
                    double imageScore = imageDetection.getPredictions().getOrDefault(activity, 0.0);
                    double audioScore = audioDetection.getPredictions().getOrDefault(activity, 0.0);
                    
                    double fusedScore = (imageScore * imageWeight) + (audioScore * soundWeight);
                    fusedPredictions.put(activity, fusedScore);
                }
            } else if (imageDetection != null) {
                fusedPredictions = imageDetection.getPredictions();
            } else {
                fusedPredictions = audioDetection.getPredictions();
            }

            // Trouver la meilleure prédiction fusionnée
            String bestActivity = Collections.max(fusedPredictions.entrySet(), 
                Map.Entry.comparingByValue()).getKey();
            double confidence = fusedPredictions.get(bestActivity);

            // Créer la détection fusionnée
            ActivityDetection detection = new ActivityDetection(bestActivity, confidence, DetectionSource.FUSION);
            detection.setPersonDetected(true);
            detection.setPersonConfidence(personConfidence);
            detection.setPredictions(fusedPredictions);
            detection.setFusionWeights(new FusionWeights(imageWeight, soundWeight));

            return detection;

        } catch (Exception e) {
            logger.error("Erreur lors de la détection par fusion: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Redimensionne une image
     */
    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, width, height, null);
        g2d.dispose();
        return resized;
    }

    /**
     * Convertit une image en INDArray pour DL4J
     */
    private INDArray imageToINDArray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        float[][][] rgbArray = new float[3][height][width];
        
        for (int i = 0; i < pixels.length; i++) {
            int x = i % width;
            int y = i / width;
            
            int rgb = pixels[i];
            rgbArray[0][y][x] = ((rgb >> 16) & 0xFF) / 255.0f; // R
            rgbArray[1][y][x] = ((rgb >> 8) & 0xFF) / 255.0f;  // G
            rgbArray[2][y][x] = (rgb & 0xFF) / 255.0f;         // B
        }
        
        return Nd4j.create(rgbArray).reshape(1, 3, height, width);
    }

    /**
     * Parse les prédictions du modèle vers une Map
     */
    private Map<String, Double> parsePredictions(INDArray output) {
        Map<String, Double> predictions = new HashMap<>();
        
        ActivityClass[] supportedClasses = ActivityClass.getImageSupportedClasses();
        float[] outputArray = output.toFloatVector();
        
        for (int i = 0; i < Math.min(outputArray.length, supportedClasses.length); i++) {
            predictions.put(supportedClasses[i].getEnglishName(), (double) outputArray[i]);
        }
        
        return predictions;
    }

    /**
     * Crée une détection depuis le cache
     */
    private ActivityDetection createDetectionFromCache(PredictionCacheEntry cached, 
                                                     DetectionSource source, double personConfidence) {
        String bestActivity = Collections.max(cached.predictions.entrySet(), 
            Map.Entry.comparingByValue()).getKey();
        double confidence = cached.predictions.get(bestActivity);

        ActivityDetection detection = new ActivityDetection(bestActivity, confidence, source);
        detection.setPersonDetected(true);
        detection.setPersonConfidence(personConfidence);
        detection.setPredictions(cached.predictions);

        return detection;
    }

    /**
     * Nettoie le cache des prédictions
     */
    private void cleanPredictionCache() {
        long currentTime = System.currentTimeMillis();
        long ttlMs = predictionCacheTTL * 1000L;
        
        predictionCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > ttlMs);
    }

    /**
     * Notifie tous les listeners de détection
     */
    private void notifyDetectionListeners(ActivityDetection detection) {
        for (Consumer<ActivityDetection> listener : detectionListeners) {
            try {
                listener.accept(detection);
            } catch (Exception e) {
                logger.error("Erreur lors de la notification d'un listener de détection: {}", e.getMessage());
            }
        }
    }

    /**
     * Ajoute un listener pour les détections d'activité
     */
    public void addDetectionListener(Consumer<ActivityDetection> listener) {
        detectionListeners.add(listener);
    }

    /**
     * Supprime un listener de détection
     */
    public void removeDetectionListener(Consumer<ActivityDetection> listener) {
        detectionListeners.remove(listener);
    }

    /**
     * Retourne la dernière détection
     */
    public ActivityDetection getLastDetection() {
        return lastDetection;
    }

    /**
     * Vérifie si la détection est en cours
     */
    public boolean isDetecting() {
        return isDetecting;
    }

    /**
     * Retourne les statistiques de détection
     */
    public Map<String, Object> getDetectionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("is_detecting", isDetecting);
        stats.put("image_buffer_size", imageBuffer.size());
        stats.put("audio_buffer_size", audioBuffer.size());
        stats.put("prediction_cache_size", predictionCache.size());
        stats.put("last_detection", lastDetection);
        stats.put("detection_listeners_count", detectionListeners.size());
        stats.put("require_person_presence", requirePersonPresence);
        stats.put("person_detection_type", personDetectionType);
        return stats;
    }

    /**
     * Classe interne pour le cache des prédictions
     */
    private static class PredictionCacheEntry {
        final Map<String, Double> predictions;
        final long timestamp;

        PredictionCacheEntry(Map<String, Double> predictions, long timestamp) {
            this.predictions = predictions;
            this.timestamp = timestamp;
        }
    }
}