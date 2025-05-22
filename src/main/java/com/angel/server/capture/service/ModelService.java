package com.angel.server.capture.service;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des modèles DL4J
 * Charge et maintient en cache les modèles pré-entraînés
 */
@Service
public class ModelService {

    private static final Logger logger = LoggerFactory.getLogger(ModelService.class);

    // Configuration des modèles depuis application.properties
    @Value("${models.directory}")
    private String modelsDirectory;

    // Modèles d'activité
    @Value("${models.activity.image.standard.path}")
    private String activityImageStandardPath;

    @Value("${models.activity.image.vgg16.path}")
    private String activityImageVgg16Path;

    @Value("${models.activity.image.resnet.path}")
    private String activityImageResnetPath;

    @Value("${models.activity.sound.standard.path}")
    private String activitySoundStandardPath;

    @Value("${models.activity.sound.spectrogram.path}")
    private String activitySoundSpectrogramPath;

    @Value("${models.activity.sound.mfcc.path}")
    private String activitySoundMfccPath;

    @Value("${models.activity.image.default}")
    private String defaultImageModel;

    @Value("${models.activity.sound.default}")
    private String defaultSoundModel;

    // Modèles de présence
    @Value("${models.presence.standard.path}")
    private String presenceStandardPath;

    @Value("${models.presence.yolo.path}")
    private String presenceYoloPath;

    @Value("${models.presence.default}")
    private String defaultPresenceModel;

    @Value("${models.presence.confidence.threshold}")
    private double presenceConfidenceThreshold;

    // Configuration de détection de personne
    @Value("${person.detection.type}")
    private String personDetectionType;

    // FaceNet (pour compatibilité)
    @Value("${models.facenet.enabled}")
    private boolean faceNetEnabled;

    @Value("${cache.models.enabled}")
    private boolean cacheEnabled;

    // Cache des modèles chargés
    private final Map<String, MultiLayerNetwork> modelCache = new ConcurrentHashMap<>();
    private MultiLayerNetwork faceNetModel;

    @PostConstruct
    public void initializeModels() {
        logger.info("Initialisation du service de modèles DL4J...");
        
        // Créer le répertoire des modèles s'il n'existe pas
        File modelsDir = new File(modelsDirectory);
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
            logger.warn("Répertoire des modèles créé: {}", modelsDirectory);
        }
        
        // Initialiser selon le type de détection de personne configuré
        switch (personDetectionType.toLowerCase()) {
            case "presence":
                logger.info("Configuration pour détection de présence avec modèles Class0/Class1");
                initializePresenceModels();
                break;
            case "facenet":
                logger.info("Configuration pour détection FaceNet (reconnaissance faciale)");
                if (faceNetEnabled) {
                    logger.warn("FaceNet non implémenté dans cette version");
                }
                break;
            case "disabled":
                logger.info("Détection de personne désactivée");
                break;
            default:
                logger.warn("Type de détection de personne inconnu: {}. Utilisation de 'presence' par défaut", personDetectionType);
                personDetectionType = "presence";
                initializePresenceModels();
                break;
        }
        
        logger.info("Service de modèles initialisé");
    }

    /**
     * Initialise les modèles de présence
     */
    private void initializePresenceModels() {
        // Vérifier que les modèles de présence existent
        File standardModel = new File(presenceStandardPath);
        File yoloModel = new File(presenceYoloPath);
        
        if (!standardModel.exists() && !yoloModel.exists()) {
            logger.warn("Aucun modèle de présence trouvé. Vérifiez que {} ou {} existent", 
                       presenceStandardPath, presenceYoloPath);
        } else {
            logger.info("Modèles de présence disponibles - Standard: {}, YOLO: {}", 
                       standardModel.exists(), yoloModel.exists());
        }
    }

    /**
     * Charge le modèle d'activité pour les images
     */
    public MultiLayerNetwork getActivityImageModel(String modelType) {
        String cacheKey = "activity_image_" + modelType;
        
        if (cacheEnabled && modelCache.containsKey(cacheKey)) {
            return modelCache.get(cacheKey);
        }
        
        String modelPath = getActivityImageModelPath(modelType);
        MultiLayerNetwork model = loadModel(modelPath);
        
        if (cacheEnabled && model != null) {
            modelCache.put(cacheKey, model);
        }
        
        return model;
    }

    /**
     * Charge le modèle d'activité pour les sons
     */
    public MultiLayerNetwork getActivitySoundModel(String modelType) {
        String cacheKey = "activity_sound_" + modelType;
        
        if (cacheEnabled && modelCache.containsKey(cacheKey)) {
            return modelCache.get(cacheKey);
        }
        
        String modelPath = getActivitySoundModelPath(modelType);
        MultiLayerNetwork model = loadModel(modelPath);
        
        if (cacheEnabled && model != null) {
            modelCache.put(cacheKey, model);
        }
        
        return model;
    }

    /**
     * Charge le modèle de présence
     */
    public MultiLayerNetwork getPresenceModel(String modelType) {
        String cacheKey = "presence_" + modelType;
        
        if (cacheEnabled && modelCache.containsKey(cacheKey)) {
            return modelCache.get(cacheKey);
        }
        
        String modelPath = getPresenceModelPath(modelType);
        MultiLayerNetwork model = loadModel(modelPath);
        
        if (cacheEnabled && model != null) {
            modelCache.put(cacheKey, model);
        }
        
        return model;
    }

    /**
     * Retourne le modèle de présence par défaut
     */
    public MultiLayerNetwork getDefaultPresenceModel() {
        return getPresenceModel(defaultPresenceModel);
    }

    /**
     * Retourne le modèle FaceNet pour la détection de personnes (compatibilité)
     */
    public MultiLayerNetwork getFaceNetModel() {
        return faceNetModel;
    }

    /**
     * Charge un modèle depuis un fichier
     */
    private MultiLayerNetwork loadModel(String modelPath) {
        try {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                logger.error("Fichier modèle non trouvé: {}", modelPath);
                return null;
            }
            
            logger.info("Chargement du modèle: {}", modelPath);
            MultiLayerNetwork model = ModelSerializer.restoreMultiLayerNetwork(modelFile);
            logger.info("Modèle chargé avec succès: {}", modelPath);
            
            return model;
        } catch (Exception e) {
            logger.error("Erreur lors du chargement du modèle {}: {}", modelPath, e.getMessage());
            return null;
        }
    }

    /**
     * Retourne le chemin du modèle d'activité pour les images
     */
    private String getActivityImageModelPath(String modelType) {
        switch (modelType.toLowerCase()) {
            case "vgg16":
                return activityImageVgg16Path;
            case "resnet":
                return activityImageResnetPath;
            case "standard":
            default:
                return activityImageStandardPath;
        }
    }

    /**
     * Retourne le chemin du modèle d'activité pour les sons
     */
    private String getActivitySoundModelPath(String modelType) {
        switch (modelType.toLowerCase()) {
            case "mfcc":
                return activitySoundMfccPath;
            case "spectrogram":
                return activitySoundSpectrogramPath;
            case "standard":
            default:
                return activitySoundStandardPath;
        }
    }

    /**
     * Retourne le chemin du modèle de présence
     */
    private String getPresenceModelPath(String modelType) {
        switch (modelType.toLowerCase()) {
            case "yolo":
                return presenceYoloPath;
            case "standard":
            default:
                return presenceStandardPath;
        }
    }

    /**
     * Retourne le modèle d'image par défaut
     */
    public MultiLayerNetwork getDefaultActivityImageModel() {
        return getActivityImageModel(defaultImageModel);
    }

    /**
     * Retourne le modèle de son par défaut
     */
    public MultiLayerNetwork getDefaultActivitySoundModel() {
        return getActivitySoundModel(defaultSoundModel);
    }

    /**
     * Vérifie si un modèle est disponible
     */
    public boolean isModelAvailable(String modelType, String category) {
        String modelPath;
        switch (category.toLowerCase()) {
            case "image":
                modelPath = getActivityImageModelPath(modelType);
                break;
            case "sound":
                modelPath = getActivitySoundModelPath(modelType);
                break;
            case "presence":
                modelPath = getPresenceModelPath(modelType);
                break;
            default:
                return false;
        }
        
        return new File(modelPath).exists();
    }

    /**
     * Retourne le type de détection de personne configuré
     */
    public String getPersonDetectionType() {
        return personDetectionType;
    }

    /**
     * Vérifie si la détection de présence est activée
     */
    public boolean isPresenceDetectionEnabled() {
        return "presence".equalsIgnoreCase(personDetectionType);
    }

    /**
     * Vérifie si FaceNet est activé
     */
    public boolean isFaceNetEnabled() {
        return "facenet".equalsIgnoreCase(personDetectionType) && faceNetEnabled;
    }

    /**
     * Retourne le seuil de confiance pour la présence
     */
    public double getPresenceConfidenceThreshold() {
        return presenceConfidenceThreshold;
    }

    /**
     * Retourne les statistiques des modèles chargés
     */
    public Map<String, Object> getModelStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cache_enabled", cacheEnabled);
        stats.put("cached_models_count", modelCache.size());
        stats.put("person_detection_type", personDetectionType);
        stats.put("facenet_enabled", isFaceNetEnabled());
        stats.put("facenet_loaded", faceNetModel != null);
        stats.put("presence_detection_enabled", isPresenceDetectionEnabled());
        
        Map<String, Boolean> availability = new HashMap<>();
        // Modèles d'activité
        availability.put("activity_image_standard", isModelAvailable("standard", "image"));
        availability.put("activity_image_vgg16", isModelAvailable("vgg16", "image"));
        availability.put("activity_image_resnet", isModelAvailable("resnet", "image"));
        availability.put("activity_sound_standard", isModelAvailable("standard", "sound"));
        availability.put("activity_sound_spectrogram", isModelAvailable("spectrogram", "sound"));
        availability.put("activity_sound_mfcc", isModelAvailable("mfcc", "sound"));
        
        // Modèles de présence
        availability.put("presence_standard", isModelAvailable("standard", "presence"));
        availability.put("presence_yolo", isModelAvailable("yolo", "presence"));
        
        stats.put("model_availability", availability);
        
        return stats;
    }

    /**
     * Vide le cache des modèles
     */
    public void clearCache() {
        modelCache.clear();
        logger.info("Cache des modèles vidé");
    }
}