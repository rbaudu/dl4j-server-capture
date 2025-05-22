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

         // Utiliser le service de preprocessing spécialisé
            INDArray input = preprocessingService.preprocessForPresenceDetection(image);
            if (input == null) {
                logger.error("Échec du preprocessing de l'image");
                return Optional.empty();
            }
            
            logger.debug("Input shape pour détection de présence: {}", 
                        Arrays.toString(input.shape()));
            
            // Vérifier que les dimensions correspondent
            long[] expectedShape = {1, 64, imageHeight, imageWidth};
            if (!Arrays.equals(input.shape(), expectedShape)) {
                logger.error("Dimensions d'entrée incorrectes. Attendu: {}, Reçu: {}", 
                           Arrays.toString(expectedShape), Arrays.toString(input.shape()));
                return Optional.empty();
            }

            // Faire la prédiction
            INDArray output = presenceModel.output(input);
            
            // Interpréter les résultats (Class0 = absence, Class1 = présence)
            double[] predictions = output.toDoubleVector();
            
            logger.debug("Prédictions brutes: {}", java.util.Arrays.toString(predictions));
            
            // Pour un modèle binaire, on s'attend à 2 classes
            if (predictions.length >= 2) {
                double absenceConfidence = predictions[0];  // Class0
                double presenceConfidence = predictions[1]; // Class1
                
                logger.debug("Prédictions - Absence: {:.3f}, Présence: {:.3f}", 
                           absenceConfidence, presenceConfidence);
                
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
            return Optional.empty();
        }
    }

    /**
     * Teste différents modèles de présence et retourne le meilleur résultat
     */
    public Optional<Double> detectPresenceWithBestModel(BufferedImage image) {
        if (!"presence".equalsIgnoreCase(personDetectionType)) {
            return Optional.empty();
        }

        // Tester d'abord le modèle par défaut
        Optional<Double> defaultResult = detectPresence(image);
        if (defaultResult.isPresent()) {
            return defaultResult;
        }

        // Si le modèle par défaut ne détecte rien, essayer l'autre modèle
        try {
            String alternativeModel = "standard".equals(defaultPresenceModel) ? "yolo" : "standard";
            MultiLayerNetwork alternativePresenceModel = modelService.getPresenceModel(alternativeModel);
            
            if (alternativePresenceModel != null) {
                BufferedImage preprocessed = preprocessImage(image);
                INDArray input = imageToINDArray(preprocessed);
                INDArray output = alternativePresenceModel.output(input);
                
                double[] predictions = output.toDoubleVector();
                if (predictions.length >= 2) {
                    double presenceConfidence = predictions[1]; // Class1
                    
                    if (presenceConfidence >= confidenceThreshold) {
                        logger.debug("Personne détectée avec modèle alternatif {} (confiance: {:.3f})", 
                                   alternativeModel, presenceConfidence);
                        return Optional.of(presenceConfidence);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Erreur avec modèle alternatif: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Préprocesse une image pour la détection de présence
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
        
        // Vérifier la disponibilité des modèles
        Map<String, Boolean> modelAvailability = new HashMap<>();
        modelAvailability.put("standard_model", modelService.isModelAvailable("standard", "presence"));
        modelAvailability.put("yolo_model", modelService.isModelAvailable("yolo", "presence"));
        stats.put("model_availability", modelAvailability);
        
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
     * Remet à zéro les statistiques
     */
    public void resetStats() {
        totalDetections = 0;
        successfulDetections = 0;
        logger.info("Statistiques de détection de présence remises à zéro");
    }
}