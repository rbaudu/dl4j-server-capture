package com.angel.server.capture.service;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service de détection de présence utilisant les modèles Class0/Class1
 * Class0 = Absence de personne, Class1 = Présence de personne
 */
@Service
public class PresenceDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(PresenceDetectionService.class);

    @Autowired
    private ImagePreprocessingService preprocessingService;

    @Autowired
    private ModelService modelService;

    // Configuration depuis application.properties
    @Value("${person.detection.type}")
    private String personDetectionType;

    @Value("${models.presence.confidence.threshold}")
    private double confidenceThreshold;

    @Value("${models.presence.default}")
    private String defaultPresenceModel;

    @Value("${detection.presence.image.width:101}")
    private int imageWidth;

    @Value("${detection.presence.image.height:101}")
    private int imageHeight;

    @Value("${detection.presence.preprocessing.channels:64}")
    private int expectedChannels;

    @Value("${detection.presence.normalization:standard}")
    private String normalizationType;

    @Value("${debug.presence.enabled:false}")
    private boolean debugEnabled;

    // Statistiques
    private int totalDetections = 0;
    private int successfulDetections = 0;

    @PostConstruct
    public void initialize() {
        if (!"presence".equalsIgnoreCase(personDetectionType)) {
            logger.info("Détection de présence désactivée (type configuré: {})", personDetectionType);
            return;
        }

        logger.info("Initialisation du service de détection de présence...");
        logger.info("Seuil de confiance configuré: {}", confidenceThreshold);
        logger.info("Modèle par défaut: {}", defaultPresenceModel);
        logger.info("Dimensions d'image pour présence: {}x{}", imageWidth, imageHeight);
        logger.info("Canaux attendus: {}", expectedChannels);
        logger.info("Type de normalisation: {}", normalizationType);
        logger.info("Debug activé: {}", debugEnabled);
        logger.info("Service de détection de présence initialisé");
    }

    /**
     * Détecte si une personne est présente dans l'image
     * @param image L'image à analyser
     * @return Optional contenant la confiance si une personne est détectée
     */
    public Optional<Double> detectPresence(BufferedImage image) {
        if (!"presence".equalsIgnoreCase(personDetectionType)) {
            return Optional.empty();
        }

        totalDetections++;

        try {
            // Obtenir le modèle de présence par défaut
            MultiLayerNetwork presenceModel = modelService.getDefaultPresenceModel();
            if (presenceModel == null) {
                logger.warn("Modèle de présence non disponible");
                return Optional.empty();
            }

            // Utiliser le service de preprocessing spécialisé avec la normalisation configurée
            INDArray input = preprocessingService.preprocessWithNormalization(image, normalizationType);
            if (input == null) {
                logger.error("Échec du preprocessing de l'image");
                return Optional.empty();
            }
            
            if (debugEnabled) {
                logger.debug("Input shape pour détection de présence: {}", 
                            Arrays.toString(input.shape()));
                logger.debug("Input stats - Min: {:.3f}, Max: {:.3f}, Mean: {:.3f}", 
                           input.minNumber().floatValue(),
                           input.maxNumber().floatValue(),
                           input.meanNumber().floatValue());
            }
            
            // Vérifier que les dimensions correspondent (64 canaux maintenant)
            long[] expectedShape = {1, expectedChannels, imageHeight, imageWidth};
            if (!Arrays.equals(input.shape(), expectedShape)) {
                logger.error("Dimensions d'entrée incorrectes. Attendu: {}, Reçu: {}", 
                           Arrays.toString(expectedShape), Arrays.toString(input.shape()));
                
                // Debug supplémentaire en cas de problème
                if (debugEnabled) {
                    logger.error("Configuration preprocessing: {}", preprocessingService.getConfigurationInfo());
                    preprocessingService.debugPreprocessing(image);
                }
                return Optional.empty();
            }

            // Faire la prédiction
            INDArray output = presenceModel.output(input);
            
            // Interpréter les résultats (Class0 = absence, Class1 = présence)
            double[] predictions = output.toDoubleVector();
            
            if (debugEnabled) {
                logger.debug("Prédictions brutes: {}", Arrays.toString(predictions));
            }
            
            // Pour un modèle binaire, on s'attend à 2 classes
            if (predictions.length >= 2) {
                double absenceConfidence = predictions[0];  // Class0
                double presenceConfidence = predictions[1]; // Class1
                
                if (debugEnabled) {
                    logger.debug("Prédictions - Absence: {:.3f}, Présence: {:.3f}", 
                               absenceConfidence, presenceConfidence);
                }
                
                // Si la confiance de présence dépasse le seuil
                if (presenceConfidence >= confidenceThreshold) {
                    successfulDetections++;
                    logger.debug("Personne détectée (confiance: {:.3f})", presenceConfidence);
                    return Optional.of(presenceConfidence);
                } else {
                    logger.debug("Aucune personne détectée (confiance présence: {:.3f} < seuil: {:.3f})", 
                               presenceConfidence, confidenceThreshold);
                    return Optional.empty();
                }
            } else {
                logger.warn("Format de sortie du modèle inattendu: {} classes", predictions.length);
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la détection de présence: {}", e.getMessage());
            
            // Debug supplémentaire en cas d'erreur
            if (debugEnabled) {
                logger.error("Stack trace complète:", e);
                try {
                    logger.info("Configuration preprocessing: {}", preprocessingService.getConfigurationInfo());
                    preprocessingService.debugPreprocessing(image);
                } catch (Exception debugException) {
                    logger.error("Erreur lors du debug: {}", debugException.getMessage());
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Teste différents modèles et types de normalisation
     */
    public Optional<Double> detectPresenceWithBestModel(BufferedImage image) {
        if (!"presence".equalsIgnoreCase(personDetectionType)) {
            return Optional.empty();
        }

        // Tester d'abord le modèle par défaut avec la normalisation configurée
        Optional<Double> defaultResult = detectPresence(image);
        if (defaultResult.isPresent()) {
            return defaultResult;
        }

        // Si échec, tester différentes normalisations
        String[] normalizationTypes = {"standard", "normalized", "standardized"};
        for (String normType : normalizationTypes) {
            if (!normType.equals(normalizationType)) {
                try {
                    logger.debug("Test avec normalisation: {}", normType);
                    Optional<Double> result = detectPresenceWithNormalization(image, normType);
                    if (result.isPresent()) {
                        logger.info("Détection réussie avec normalisation alternative: {}", normType);
                        return result;
                    }
                } catch (Exception e) {
                    logger.debug("Erreur avec normalisation {}: {}", normType, e.getMessage());
                }
            }
        }

        // Si toujours pas de résultat, essayer l'autre modèle
        try {
            String alternativeModel = "standard".equals(defaultPresenceModel) ? "yolo" : "standard";
            MultiLayerNetwork alternativePresenceModel = modelService.getPresenceModel(alternativeModel);
            
            if (alternativePresenceModel != null) {
                logger.debug("Test avec modèle alternatif: {}", alternativeModel);
                
                for (String normType : normalizationTypes) {
                    INDArray input = preprocessingService.preprocessWithNormalization(image, normType);
                    if (input != null && Arrays.equals(input.shape(), new long[]{1, expectedChannels, imageHeight, imageWidth})) {
                        INDArray output = alternativePresenceModel.output(input);
                        double[] predictions = output.toDoubleVector();
                        
                        if (predictions.length >= 2) {
                            double presenceConfidence = predictions[1]; // Class1
                            
                            if (presenceConfidence >= confidenceThreshold) {
                                logger.info("Personne détectée avec modèle alternatif {} et normalisation {} (confiance: {:.3f})", 
                                           alternativeModel, normType, presenceConfidence);
                                return Optional.of(presenceConfidence);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Erreur avec modèle alternatif: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Détection avec un type de normalisation spécifique
     */
    private Optional<Double> detectPresenceWithNormalization(BufferedImage image, String normType) {
        try {
            MultiLayerNetwork presenceModel = modelService.getDefaultPresenceModel();
            if (presenceModel == null) {
                return Optional.empty();
            }

            INDArray input = preprocessingService.preprocessWithNormalization(image, normType);
            if (input == null) {
                return Optional.empty();
            }

            // Vérifier les dimensions
            long[] expectedShape = {1, expectedChannels, imageHeight, imageWidth};
            if (!Arrays.equals(input.shape(), expectedShape)) {
                logger.debug("Dimensions incorrectes pour normalisation {}: attendu {}, reçu {}", 
                           normType, Arrays.toString(expectedShape), Arrays.toString(input.shape()));
                return Optional.empty();
            }

            INDArray output = presenceModel.output(input);
            double[] predictions = output.toDoubleVector();

            if (predictions.length >= 2) {
                double presenceConfidence = predictions[1];
                if (presenceConfidence >= confidenceThreshold) {
                    return Optional.of(presenceConfidence);
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            logger.debug("Erreur avec normalisation {}: {}", normType, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Préprocesse une image pour la détection de présence (méthode legacy)
     */
    private BufferedImage preprocessImage(BufferedImage original) {
        // Redimensionner à la taille exacte attendue par le modèle de présence
        BufferedImage resized = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, imageWidth, imageHeight, null);
        g2d.dispose();
        
        return resized;
    }

    /**
     * Convertit une image en INDArray pour DL4J (méthode legacy - 3 canaux)
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
            // Normalisation standard [0, 1]
            rgbArray[0][y][x] = ((rgb >> 16) & 0xFF) / 255.0f; // R
            rgbArray[1][y][x] = ((rgb >> 8) & 0xFF) / 255.0f;  // G
            rgbArray[2][y][x] = (rgb & 0xFF) / 255.0f;         // B
        }
        
        return Nd4j.create(rgbArray).reshape(1, 3, height, width);
    }

    /**
     * Vérifie si la détection de présence est activée
     */
    public boolean isEnabled() {
        return "presence".equalsIgnoreCase(personDetectionType);
    }

    /**
     * Retourne les statistiques de détection
     */
    public Map<String, Object> getDetectionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", isEnabled());
        stats.put("detection_type", personDetectionType);
        stats.put("total_detections", totalDetections);
        stats.put("successful_detections", successfulDetections);
        stats.put("success_rate", totalDetections > 0 ? 
            (double) successfulDetections / totalDetections : 0.0);
        stats.put("confidence_threshold", confidenceThreshold);
        stats.put("image_dimensions", imageWidth + "x" + imageHeight);
        stats.put("expected_channels", expectedChannels);
        stats.put("normalization_type", normalizationType);
        stats.put("debug_enabled", debugEnabled);
        
        // Vérifier la disponibilité des modèles
        Map<String, Boolean> modelAvailability = new HashMap<>();
        modelAvailability.put("standard_model", modelService.isModelAvailable("standard", "presence"));
        modelAvailability.put("yolo_model", modelService.isModelAvailable("yolo", "presence"));
        stats.put("model_availability", modelAvailability);
        
        // Informations de configuration du preprocessing
        stats.put("preprocessing_config", preprocessingService.getConfigurationInfo());
        
        return stats;
    }

    /**
     * Met à jour le seuil de confiance
     */
    public void updateConfidenceThreshold(double newThreshold) {
        this.confidenceThreshold = newThreshold;
        logger.info("Seuil de confiance de présence mis à jour: {}", newThreshold);
    }

    /**
     * Met à jour le type de normalisation
     */
    public void updateNormalizationType(String newNormalizationType) {
        this.normalizationType = newNormalizationType;
        logger.info("Type de normalisation mis à jour: {}", newNormalizationType);
    }

    /**
     * Active/désactive le mode debug
     */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        logger.info("Mode debug {}", enabled ? "activé" : "désactivé");
    }

    /**
     * Remet à zéro les statistiques
     */
    public void resetStats() {
        totalDetections = 0;
        successfulDetections = 0;
        logger.info("Statistiques de détection de présence remises à zéro");
    }

    /**
     * Test de configuration - à utiliser pour diagnostiquer les problèmes
     */
    public void testConfiguration(BufferedImage testImage) {
        logger.info("=== TEST DE CONFIGURATION ===");
        logger.info("Configuration du service:");
        logger.info("- Type de détection: {}", personDetectionType);
        logger.info("- Modèle par défaut: {}", defaultPresenceModel);
        logger.info("- Dimensions attendues: {}x{}", imageWidth, imageHeight);
        logger.info("- Canaux attendus: {}", expectedChannels);
        logger.info("- Normalisation: {}", normalizationType);
        logger.info("- Seuil de confiance: {}", confidenceThreshold);
        
        if (testImage != null) {
            logger.info("Test avec image {}x{}", testImage.getWidth(), testImage.getHeight());
            preprocessingService.debugPreprocessing(testImage);
        }
        
        // Test de disponibilité des modèles
        MultiLayerNetwork model = modelService.getDefaultPresenceModel();
        logger.info("Modèle par défaut disponible: {}", model != null);
        
        if (model != null) {
            logger.info("Configuration du modèle:");
            try {
                // Afficher des informations sur le modèle si possible
                logger.info("- Nombre de couches: {}", model.getnLayers());
                logger.info("- Configuration d'entrée: {}", model.getLayerWiseConfigurations().getInputPreProcess(0));
            } catch (Exception e) {
                logger.warn("Impossible d'analyser la configuration du modèle: {}", e.getMessage());
            }
        }
        
        logger.info("=== FIN TEST CONFIGURATION ===");
    }
}