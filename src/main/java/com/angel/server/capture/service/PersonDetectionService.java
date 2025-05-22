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
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de détection de personne utilisant FaceNet
 * Détecte si une personne spécifique est présente dans l'image
 */
@Service
public class PersonDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(PersonDetectionService.class);

    @Autowired
    private ModelService modelService;

    // Configuration depuis application.properties
    @Value("${models.facenet.enabled}")
    private boolean faceNetEnabled;

    @Value("${models.facenet.confidence.threshold}")
    private double confidenceThreshold;

    @Value("${models.facenet.faces.directory}")
    private String facesDirectory;

    // Cache des embeddings de référence
    private final Map<String, INDArray> referenceEmbeddings = new ConcurrentHashMap<>();
    
    // Statistiques
    private int totalDetections = 0;
    private int successfulDetections = 0;

    @PostConstruct
    public void initialize() {
        if (!faceNetEnabled) {
            logger.info("Détection de personne désactivée dans la configuration");
            return;
        }

        logger.info("Initialisation du service de détection de personne...");
        
        // Créer le répertoire des visages s'il n'existe pas
        File facesDir = new File(facesDirectory);
        if (!facesDir.exists()) {
            facesDir.mkdirs();
            logger.info("Répertoire des visages créé: {}", facesDirectory);
        }

        // Charger les visages de référence
        loadReferenceImages();
        
        logger.info("Service de détection de personne initialisé");
    }

    /**
     * Détecte si une personne connue est présente dans l'image
     * @param image L'image à analyser
     * @return Optional contenant la confiance si une personne est détectée
     */
    public Optional<Double> detectPerson(BufferedImage image) {
        if (!faceNetEnabled) {
            return Optional.empty();
        }

        totalDetections++;

        try {
            // Obtenir le modèle FaceNet
            MultiLayerNetwork faceNetModel = modelService.getFaceNetModel();
            if (faceNetModel == null) {
                logger.warn("Modèle FaceNet non disponible");
                return Optional.empty();
            }

            // Préprocesser l'image
            BufferedImage preprocessed = preprocessImage(image);
            INDArray input = imageToINDArray(preprocessed);

            // Extraire l'embedding
            INDArray embedding = faceNetModel.output(input);

            // Comparer avec les embeddings de référence
            double maxSimilarity = 0.0;
            String bestMatch = null;

            for (Map.Entry<String, INDArray> entry : referenceEmbeddings.entrySet()) {
                double similarity = cosineSimilarity(embedding, entry.getValue());
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    bestMatch = entry.getKey();
                }
            }

            // Vérifier le seuil de confiance
            if (maxSimilarity >= confidenceThreshold) {
                successfulDetections++;
                logger.debug("Personne détectée: {} (similarité: {:.3f})", bestMatch, maxSimilarity);
                return Optional.of(maxSimilarity);
            } else {
                logger.debug("Aucune personne connue détectée (meilleure similarité: {:.3f})", maxSimilarity);
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la détection de personne: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Ajoute une nouvelle image de référence
     * @param personName Nom de la personne
     * @param image Image de la personne
     * @return true si l'ajout a réussi
     */
    public boolean addReferenceImage(String personName, BufferedImage image) {
        if (!faceNetEnabled) {
            return false;
        }

        try {
            // Obtenir le modèle FaceNet
            MultiLayerNetwork faceNetModel = modelService.getFaceNetModel();
            if (faceNetModel == null) {
                logger.warn("Modèle FaceNet non disponible");
                return false;
            }

            // Préprocesser l'image
            BufferedImage preprocessed = preprocessImage(image);
            INDArray input = imageToINDArray(preprocessed);

            // Extraire l'embedding
            INDArray embedding = faceNetModel.output(input);

            // Sauvegarder l'embedding
            referenceEmbeddings.put(personName, embedding);

            // Sauvegarder l'image sur disque
            File imageFile = new File(facesDirectory, personName + ".jpg");
            ImageIO.write(image, "jpg", imageFile);

            logger.info("Image de référence ajoutée pour: {}", personName);
            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'ajout de l'image de référence pour {}: {}", personName, e.getMessage());
            return false;
        }
    }

    /**
     * Supprime une image de référence
     * @param personName Nom de la personne
     * @return true si la suppression a réussi
     */
    public boolean removeReferenceImage(String personName) {
        if (!faceNetEnabled) {
            return false;
        }

        try {
            // Supprimer de la mémoire
            referenceEmbeddings.remove(personName);

            // Supprimer le fichier
            File imageFile = new File(facesDirectory, personName + ".jpg");
            if (imageFile.exists()) {
                imageFile.delete();
            }

            logger.info("Image de référence supprimée pour: {}", personName);
            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de la suppression de l'image de référence pour {}: {}", personName, e.getMessage());
            return false;
        }
    }

    /**
     * Charge toutes les images de référence depuis le répertoire
     */
    private void loadReferenceImages() {
        if (!faceNetEnabled) {
            return;
        }

        File facesDir = new File(facesDirectory);
        File[] imageFiles = facesDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".jpg") || 
            name.toLowerCase().endsWith(".jpeg") || 
            name.toLowerCase().endsWith(".png"));

        if (imageFiles == null || imageFiles.length == 0) {
            logger.info("Aucune image de référence trouvée dans {}", facesDirectory);
            return;
        }

        MultiLayerNetwork faceNetModel = modelService.getFaceNetModel();
        if (faceNetModel == null) {
            logger.warn("Modèle FaceNet non disponible pour charger les images de référence");
            return;
        }

        int loaded = 0;
        for (File imageFile : imageFiles) {
            try {
                String personName = getNameFromFilename(imageFile.getName());
                BufferedImage image = ImageIO.read(imageFile);
                
                if (image != null) {
                    // Préprocesser l'image
                    BufferedImage preprocessed = preprocessImage(image);
                    INDArray input = imageToINDArray(preprocessed);

                    // Extraire l'embedding
                    INDArray embedding = faceNetModel.output(input);
                    referenceEmbeddings.put(personName, embedding);
                    
                    loaded++;
                    logger.debug("Image de référence chargée: {}", personName);
                }
            } catch (Exception e) {
                logger.error("Erreur lors du chargement de l'image {}: {}", imageFile.getName(), e.getMessage());
            }
        }

        logger.info("{} images de référence chargées", loaded);
    }

    /**
     * Préprocesse une image pour FaceNet
     */
    private BufferedImage preprocessImage(BufferedImage original) {
        // Redimensionner à 160x160 (taille attendue par FaceNet)
        int targetSize = 160;
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, targetSize, targetSize, null);
        g2d.dispose();
        
        return resized;
    }

    /**
     * Convertit une image en INDArray pour FaceNet
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
            // Normalisation pour FaceNet [-1, 1]
            rgbArray[0][y][x] = (((rgb >> 16) & 0xFF) / 127.5f) - 1.0f; // R
            rgbArray[1][y][x] = (((rgb >> 8) & 0xFF) / 127.5f) - 1.0f;  // G
            rgbArray[2][y][x] = ((rgb & 0xFF) / 127.5f) - 1.0f;         // B
        }
        
        return Nd4j.create(rgbArray).reshape(1, 3, height, width);
    }

    /**
     * Calcule la similarité cosinus entre deux embeddings
     */
    private double cosineSimilarity(INDArray a, INDArray b) {
        INDArray dotProduct = a.mmul(b.transpose());
        double normA = a.norm2Number().doubleValue();
        double normB = b.norm2Number().doubleValue();
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct.getDouble(0) / (normA * normB);
    }

    /**
     * Extrait le nom de la personne depuis le nom de fichier
     */
    private String getNameFromFilename(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    /**
     * Retourne la liste des personnes de référence
     */
    public Set<String> getReferencePersons() {
        return new HashSet<>(referenceEmbeddings.keySet());
    }

    /**
     * Vérifie si la détection de personne est activée
     */
    public boolean isEnabled() {
        return faceNetEnabled;
    }

    /**
     * Retourne les statistiques de détection
     */
    public Map<String, Object> getDetectionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", faceNetEnabled);
        stats.put("reference_persons_count", referenceEmbeddings.size());
        stats.put("total_detections", totalDetections);
        stats.put("successful_detections", successfulDetections);
        stats.put("success_rate", totalDetections > 0 ? 
            (double) successfulDetections / totalDetections : 0.0);
        stats.put("confidence_threshold", confidenceThreshold);
        stats.put("reference_persons", getReferencePersons());
        
        return stats;
    }

    /**
     * Recharge les images de référence
     */
    public void reloadReferenceImages() {
        referenceEmbeddings.clear();
        loadReferenceImages();
    }

    /**
     * Met à jour le seuil de confiance
     */
    public void updateConfidenceThreshold(double newThreshold) {
        this.confidenceThreshold = newThreshold;
        logger.info("Seuil de confiance mis à jour: {}", newThreshold);
    }
}