package com.angel.server.capture.controller;

import com.angel.server.capture.service.PresenceDetectionService;
import com.angel.server.capture.service.ModelService;
import com.angel.server.capture.service.VideoCaptureService;
import com.angel.server.capture.service.ImagePreprocessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Contrôleur de diagnostic pour déboguer les problèmes de modèles
 */
@RestController
@RequestMapping("/api/v1/debug")
public class DebugController {
    
    @Autowired
    private PresenceDetectionService presenceDetectionService;
    
    @Autowired
    private ModelService modelService;
    
    @Autowired
    private VideoCaptureService videoCaptureService;
    
    @Autowired
    private ImagePreprocessingService preprocessingService;
    
    /**
     * Diagnostic complet du système
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> systemDiagnostic() {
        Map<String, Object> diagnostic = new HashMap<>();
        
        // Statut des services
        diagnostic.put("presence_detection", presenceDetectionService.getDetectionStats());
        diagnostic.put("model_service", modelService.getModelStats());
        diagnostic.put("video_capture", Map.of(
            "capturing", videoCaptureService.isCapturing(),
            "active_sources", videoCaptureService.getActiveSourcesCount(),
            "sources", videoCaptureService.getActiveSources()
        ));
        diagnostic.put("preprocessing", Map.of(
            "config", preprocessingService.getConfigurationInfo(),
            "valid", preprocessingService.isConfigurationValid()
        ));
        
        return ResponseEntity.ok(diagnostic);
    }
    
    /**
     * Test de configuration du modèle de présence
     */
    @GetMapping("/presence-model")
    public ResponseEntity<Map<String, Object>> testPresenceModel() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Essayer de charger le modèle
            var model = modelService.getDefaultPresenceModel();
            if (model == null) {
                result.put("status", "error");
                result.put("message", "Modèle de présence non disponible");
                return ResponseEntity.ok(result);
            }
            
            result.put("status", "success");
            result.put("model_available", true);
            result.put("num_layers", model.getnLayers());
            
            // Analyser la configuration du modèle
            Map<String, Object> modelConfig = new HashMap<>();
            
            try {
                var inputPreProcessor = model.getLayerWiseConfigurations().getInputPreProcess(0);
                if (inputPreProcessor != null) {
                    modelConfig.put("input_preprocessor", inputPreProcessor.toString());
                }
                
                // Informations sur les couches
                for (int i = 0; i < model.getnLayers(); i++) {
                    var layer = model.getLayer(i);
                    if (layer != null) {
                        Map<String, Object> layerInfo = new HashMap<>();
                        layerInfo.put("type", layer.getClass().getSimpleName());
                        layerInfo.put("config", layer.conf().toString());
                        modelConfig.put("layer_" + i, layerInfo);
                    }
                }
                
            } catch (Exception e) {
                modelConfig.put("analysis_error", e.getMessage());
            }
            
            result.put("model_config", modelConfig);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Test avec différentes tailles d'image
     */
    @GetMapping("/test-dimensions")
    public ResponseEntity<Map<String, Object>> testDimensions() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            var model = modelService.getDefaultPresenceModel();
            if (model == null) {
                result.put("error", "Modèle non disponible");
                return ResponseEntity.ok(result);
            }
            
            // Tester différentes tailles
            int[] testSizes = {101, 64, 32, 224, 128};
            Map<String, Object> testResults = new HashMap<>();
            
            for (int size : testSizes) {
                try {
                    // Créer un tensor de test
                    org.nd4j.linalg.api.ndarray.INDArray testInput = 
                        org.nd4j.linalg.factory.Nd4j.rand(1, 64, size, size);
                    
                    var output = model.output(testInput);
                    
                    testResults.put("size_" + size, Map.of(
                        "input_shape", java.util.Arrays.toString(testInput.shape()),
                        "output_shape", java.util.Arrays.toString(output.shape()),
                        "success", true
                    ));
                    
                } catch (Exception e) {
                    testResults.put("size_" + size, Map.of(
                        "success", false,
                        "error", e.getMessage()
                    ));
                }
            }
            
            result.put("dimension_tests", testResults);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Force un test de détection avec image factice
     */
    @GetMapping("/test-detection")
    public ResponseEntity<Map<String, Object>> testDetection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Créer une image de test
            java.awt.image.BufferedImage testImage = 
                new java.awt.image.BufferedImage(640, 480, java.awt.image.BufferedImage.TYPE_INT_RGB);
            
            // Remplir avec un motif de test
            java.awt.Graphics2D g2d = testImage.createGraphics();
            g2d.setColor(java.awt.Color.BLUE);
            g2d.fillRect(0, 0, 640, 480);
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillOval(200, 150, 240, 180);
            g2d.dispose();
            
            // Tester le preprocessing
            var preprocessed = preprocessingService.preprocessWithNormalization(testImage, "standard");
            
            if (preprocessed != null) {
                result.put("preprocessing", Map.of(
                    "success", true,
                    "input_image_size", testImage.getWidth() + "x" + testImage.getHeight(),
                    "preprocessed_shape", java.util.Arrays.toString(preprocessed.shape()),
                    "preprocessed_stats", Map.of(
                        "min", preprocessed.minNumber().floatValue(),
                        "max", preprocessed.maxNumber().floatValue(),
                        "mean", preprocessed.meanNumber().floatValue()
                    )
                ));
                
                // Tester la détection
                presenceDetectionService.testConfiguration(testImage);
                var detectionResult = presenceDetectionService.detectPresence(testImage);
                
                result.put("detection", Map.of(
                    "success", detectionResult.isPresent(),
                    "confidence", detectionResult.orElse(0.0)
                ));
                
            } else {
                result.put("preprocessing", Map.of(
                    "success", false,
                    "error", "Preprocessing failed"
                ));
            }
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}