package com.angel.server.capture.service;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

@Service
public class ImagePreprocessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(ImagePreprocessingService.class);
    
    @Value("${detection.presence.image.width:101}")
    private int targetWidth;
    
    @Value("${detection.presence.image.height:101}")
    private int targetHeight;
    
    /**
     * Préprocesse une image pour la détection de présence
     * Génère un INDArray compatible avec un modèle CNN standard (3 canaux RGB)
     */
    public INDArray preprocessForPresenceDetection(BufferedImage image) {
        try {
            // 1. Redimensionner à la taille cible
            BufferedImage resized = resizeImage(image, targetWidth, targetHeight);
            
            // 2. Convertir en INDArray avec 3 canaux RGB (format standard)
            INDArray features = imageToINDArray(resized);
            
            // 3. Vérifier les dimensions finales
            long[] expectedShape = {1, 3, targetHeight, targetWidth};
            if (!Arrays.equals(features.shape(), expectedShape)) {
                logger.error("Dimensions incorrectes après preprocessing. Attendu: {}, Obtenu: {}", 
                            Arrays.toString(expectedShape), Arrays.toString(features.shape()));
                return null;
            }
            
            logger.debug("Preprocessing réussi, dimensions finales: {}", 
                        Arrays.toString(features.shape()));
            
            return features;
            
        } catch (Exception e) {
            logger.error("Erreur lors du preprocessing de l'image: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Redimensionne une image aux dimensions cibles
     */
    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        
        // Utiliser la meilleure qualité de redimensionnement
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, 
                            RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(original, 0, 0, width, height, null);
        g2d.dispose();
        
        return resized;
    }
    
    /**
     * Convertit une BufferedImage en INDArray pour DL4J
     * Format de sortie: [1, 3, height, width] (NCHW)
     */
    private INDArray imageToINDArray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Obtenir tous les pixels RGB de l'image
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        // Créer un tableau pour stocker les 3 canaux RGB
        float[][][] rgbArray = new float[3][height][width];
        
        // Extraire et normaliser les canaux RGB
        for (int i = 0; i < pixels.length; i++) {
            int x = i % width;
            int y = i / width;
            
            int rgb = pixels[i];
            
            // Extraire les composantes RGB et normaliser [0, 255] -> [0, 1]
            rgbArray[0][y][x] = ((rgb >> 16) & 0xFF) / 255.0f; // Canal Rouge
            rgbArray[1][y][x] = ((rgb >> 8) & 0xFF) / 255.0f;  // Canal Vert
            rgbArray[2][y][x] = (rgb & 0xFF) / 255.0f;         // Canal Bleu
        }
        
        // Convertir en INDArray avec la forme [1, 3, height, width]
        return Nd4j.create(rgbArray).reshape(1, 3, height, width);
    }
    
    /**
     * Alternative avec normalisation centrée et réduite (mean=0.5, std=0.5)
     * Utilisée par certains modèles pré-entraînés
     */
    public INDArray preprocessForPresenceDetectionNormalized(BufferedImage image) {
        try {
            BufferedImage resized = resizeImage(image, targetWidth, targetHeight);
            
            int width = resized.getWidth();
            int height = resized.getHeight();
            
            int[] pixels = new int[width * height];
            resized.getRGB(0, 0, width, height, pixels, 0, width);
            
            float[][][] rgbArray = new float[3][height][width];
            
            for (int i = 0; i < pixels.length; i++) {
                int x = i % width;
                int y = i / width;
                
                int rgb = pixels[i];
                
                // Normalisation centrée et réduite: (pixel/255 - 0.5) / 0.5 = (pixel/255)*2 - 1
                // Résultat dans [-1, 1]
                rgbArray[0][y][x] = (((rgb >> 16) & 0xFF) / 255.0f) * 2.0f - 1.0f; // Rouge
                rgbArray[1][y][x] = (((rgb >> 8) & 0xFF) / 255.0f) * 2.0f - 1.0f;  // Vert
                rgbArray[2][y][x] = ((rgb & 0xFF) / 255.0f) * 2.0f - 1.0f;         // Bleu
            }
            
            return Nd4j.create(rgbArray).reshape(1, 3, height, width);
            
        } catch (Exception e) {
            logger.error("Erreur lors du preprocessing normalisé: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Preprocessing avec normalisation ImageNet (pour modèles pré-entraînés)
     * Mean: [0.485, 0.456, 0.406], Std: [0.229, 0.224, 0.225]
     */
    public INDArray preprocessForPresenceDetectionImageNet(BufferedImage image) {
        try {
            BufferedImage resized = resizeImage(image, targetWidth, targetHeight);
            
            int width = resized.getWidth();
            int height = resized.getHeight();
            
            int[] pixels = new int[width * height];
            resized.getRGB(0, 0, width, height, pixels, 0, width);
            
            float[][][] rgbArray = new float[3][height][width];
            
            // Moyennes et écarts-types ImageNet
            float[] mean = {0.485f, 0.456f, 0.406f};
            float[] std = {0.229f, 0.224f, 0.225f};
            
            for (int i = 0; i < pixels.length; i++) {
                int x = i % width;
                int y = i / width;
                
                int rgb = pixels[i];
                
                // Normalisation ImageNet: (pixel/255 - mean) / std
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;
                
                rgbArray[0][y][x] = (r - mean[0]) / std[0]; // Rouge
                rgbArray[1][y][x] = (g - mean[1]) / std[1]; // Vert
                rgbArray[2][y][x] = (b - mean[2]) / std[2]; // Bleu
            }
            
            return Nd4j.create(rgbArray).reshape(1, 3, height, width);
            
        } catch (Exception e) {
            logger.error("Erreur lors du preprocessing ImageNet: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Méthode de convenance pour choisir automatiquement la normalisation
     * @param image L'image à préprocesser
     * @param normalizationType Type de normalisation ("standard", "normalized", "imagenet")
     * @return INDArray preprocessé
     */
    public INDArray preprocessWithNormalization(BufferedImage image, String normalizationType) {
        switch (normalizationType.toLowerCase()) {
            case "normalized":
                return preprocessForPresenceDetectionNormalized(image);
            case "imagenet":
                return preprocessForPresenceDetectionImageNet(image);
            case "standard":
            default:
                return preprocessForPresenceDetection(image);
        }
    }
    
    /**
     * Vérifie si le preprocessing est configuré correctement
     */
    public boolean isConfigurationValid() {
        return targetWidth > 0 && targetHeight > 0;
    }
    
    /**
     * Retourne les paramètres de configuration actuels
     */
    public String getConfigurationInfo() {
        return String.format("Dimensions: %dx%d, Format: RGB (3 canaux)", 
                           targetWidth, targetHeight);
    }
    
    /**
     * Teste différentes normalisations et retourne des statistiques
     */
    public void debugPreprocessing(BufferedImage image) {
        logger.info("=== DEBUG PREPROCESSING ===");
        logger.info("Image source: {}x{}", image.getWidth(), image.getHeight());
        logger.info("Configuration cible: 3 canaux RGB, {}x{}", targetWidth, targetHeight);
        
        INDArray standard = preprocessForPresenceDetection(image);
        if (standard != null) {
            logger.info("Standard - Shape: {}, Min: {:.3f}, Max: {:.3f}, Mean: {:.3f}", 
                       Arrays.toString(standard.shape()), 
                       standard.minNumber().floatValue(),
                       standard.maxNumber().floatValue(),
                       standard.meanNumber().floatValue());
        }
        
        INDArray normalized = preprocessForPresenceDetectionNormalized(image);
        if (normalized != null) {
            logger.info("Normalized - Shape: {}, Min: {:.3f}, Max: {:.3f}, Mean: {:.3f}", 
                       Arrays.toString(normalized.shape()),
                       normalized.minNumber().floatValue(),
                       normalized.maxNumber().floatValue(),
                       normalized.meanNumber().floatValue());
        }
        
        INDArray imagenet = preprocessForPresenceDetectionImageNet(image);
        if (imagenet != null) {
            logger.info("ImageNet - Shape: {}, Min: {:.3f}, Max: {:.3f}, Mean: {:.3f}", 
                       Arrays.toString(imagenet.shape()),
                       imagenet.minNumber().floatValue(),
                       imagenet.maxNumber().floatValue(),
                       imagenet.meanNumber().floatValue());
        }
        
        logger.info("=== FIN DEBUG ===");
    }
}