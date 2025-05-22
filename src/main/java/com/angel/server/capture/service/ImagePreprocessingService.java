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
    
    @Value("${detection.presence.preprocessing.channels:64}")
    private int targetChannels;
    
    @Value("${detection.presence.image.width:101}")
    private int targetWidth;
    
    @Value("${detection.presence.image.height:101}")
    private int targetHeight;
    
    /**
     * Préprocesse une image pour la détection de présence
     * Extrait des features pour obtenir 64 canaux
     */
    public INDArray preprocessForPresenceDetection(BufferedImage image) {
        try {
            // 1. Redimensionner à la taille cible
            BufferedImage resized = resizeImage(image, targetWidth, targetHeight);
            
            // 2. Extraire des features pour obtenir 64 canaux
            INDArray features = extractFeaturesFromBufferedImage(resized);
            
            // 3. Vérifier les dimensions finales
            if (features.shape().length != 4 || features.shape()[1] != targetChannels) {
                logger.error("Dimensions incorrectes après preprocessing: {}", 
                            Arrays.toString(features.shape()));
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
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, 
                            RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(original, 0, 0, width, height, null);
        g2d.dispose();
        return resized;
    }
    
    /**
     * Extrait des features depuis une BufferedImage pour obtenir 64 canaux
     * Utilise uniquement les API Java standard, sans OpenCV
     */
    private INDArray extractFeaturesFromBufferedImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Obtenir tous les pixels de l'image
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        // Créer un tableau pour stocker les 64 features
        float[][][] features = new float[targetChannels][height][width];
        
        // Extraire différents types de features
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = y * width + x;
                int rgb = pixels[pixelIndex];
                
                // Extraire les composantes RGB
                double r = ((rgb >> 16) & 0xFF) / 255.0;
                double g = ((rgb >> 8) & 0xFF) / 255.0;
                double b = (rgb & 0xFF) / 255.0;
                
                // Calculer différents types de features
                features[0][y][x] = (float) r; // Rouge
                features[1][y][x] = (float) g; // Vert
                features[2][y][x] = (float) b; // Bleu
                
                // Niveaux de gris et variations
                features[3][y][x] = (float) (0.299 * r + 0.587 * g + 0.114 * b); // Luminance
                features[4][y][x] = (float) Math.max(Math.max(r, g), b); // Max RGB
                features[5][y][x] = (float) Math.min(Math.min(r, g), b); // Min RGB
                features[6][y][x] = (float) ((r + g + b) / 3.0); // Moyenne RGB
                features[7][y][x] = (float) Math.sqrt(r * r + g * g + b * b); // Norme euclidienne
                
                // Features de différences et ratios
                features[8][y][x] = (float) Math.abs(r - g);
                features[9][y][x] = (float) Math.abs(g - b);
                features[10][y][x] = (float) Math.abs(r - b);
                features[11][y][x] = g > 0 ? (float) (r / g) : 0;
                features[12][y][x] = b > 0 ? (float) (g / b) : 0;
                features[13][y][x] = r > 0 ? (float) (b / r) : 0;
                
                // Features de saturation et teinte (approximation HSV)
                double max = Math.max(Math.max(r, g), b);
                double min = Math.min(Math.min(r, g), b);
                double delta = max - min;
                
                features[14][y][x] = max > 0 ? (float) (delta / max) : 0; // Saturation
                features[15][y][x] = (float) max; // Valeur (brightness)
                
                // Features de gradients locaux (si pas en bordure)
                if (x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                    // Gradient horizontal et vertical
                    int leftPixel = pixels[y * width + (x - 1)];
                    int rightPixel = pixels[y * width + (x + 1)];
                    int topPixel = pixels[(y - 1) * width + x];
                    int bottomPixel = pixels[(y + 1) * width + x];
                    
                    double leftLum = getLuminance(leftPixel);
                    double rightLum = getLuminance(rightPixel);
                    double topLum = getLuminance(topPixel);
                    double bottomLum = getLuminance(bottomPixel);
                    
                    features[16][y][x] = (float) (rightLum - leftLum); // Gradient horizontal
                    features[17][y][x] = (float) (bottomLum - topLum); // Gradient vertical
                    features[18][y][x] = (float) Math.sqrt(Math.pow(rightLum - leftLum, 2) + 
                                                          Math.pow(bottomLum - topLum, 2)); // Magnitude du gradient
                } else {
                    features[16][y][x] = 0;
                    features[17][y][x] = 0;
                    features[18][y][x] = 0;
                }
                
                // Features basées sur des transformations mathématiques
                for (int f = 19; f < targetChannels; f++) {
                    features[f][y][x] = generateAdditionalFeature(r, g, b, x, y, f);
                }
            }
        }
        
        // Convertir en INDArray avec la forme [1, channels, height, width]
        return Nd4j.create(features).reshape(1, targetChannels, height, width);
    }
    
    /**
     * Calcule la luminance d'un pixel RGB
     */
    private double getLuminance(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }
    
    /**
     * Génère des features additionnelles pour atteindre 64 canaux
     */
    private float generateAdditionalFeature(double r, double g, double b, int x, int y, int featureIndex) {
        // Générer des features basées sur des combinaisons et transformations
        switch (featureIndex % 20) {
            case 0: return (float) (r * g * b); // Produit RGB
            case 1: return (float) Math.pow(r, 2); // R au carré
            case 2: return (float) Math.pow(g, 2); // G au carré
            case 3: return (float) Math.pow(b, 2); // B au carré
            case 4: return (float) Math.sqrt(r); // Racine de R
            case 5: return (float) Math.sqrt(g); // Racine de G
            case 6: return (float) Math.sqrt(b); // Racine de B
            case 7: return (float) Math.sin(r * Math.PI); // Transformation sinusoïdale
            case 8: return (float) Math.cos(g * Math.PI); // Transformation cosinusoïdale
            case 9: return (float) Math.tan(b * Math.PI / 4); // Transformation tangente
            case 10: return (float) (r + g - b); // Combinaison linéaire 1
            case 11: return (float) (r - g + b); // Combinaison linéaire 2
            case 12: return (float) (-r + g + b); // Combinaison linéaire 3
            case 13: return (float) (r * 0.5 + g * 0.3 + b * 0.2); // Moyenne pondérée 1
            case 14: return (float) (r * 0.2 + g * 0.5 + b * 0.3); // Moyenne pondérée 2
            case 15: return (float) (r * 0.3 + g * 0.2 + b * 0.5); // Moyenne pondérée 3
            case 16: return (float) Math.log(1 + r); // Log de R
            case 17: return (float) Math.log(1 + g); // Log de G
            case 18: return (float) Math.log(1 + b); // Log de B
            case 19: return (float) ((x % 10) * 0.1 * (y % 10) * 0.1 * (r + g + b)); // Feature spatiale
            default: return (float) ((r + g + b) / 3.0 + Math.random() * 0.01); // Bruit contrôlé
        }
    }
    
    /**
     * Vérifie si le preprocessing est configuré correctement
     */
    public boolean isConfigurationValid() {
        return targetChannels > 0 && targetWidth > 0 && targetHeight > 0;
    }
    
    /**
     * Retourne les paramètres de configuration actuels
     */
    public String getConfigurationInfo() {
        return String.format("Channels: %d, Dimensions: %dx%d", 
                           targetChannels, targetWidth, targetHeight);
    }
}