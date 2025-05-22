package com.angel.server.capture.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Modèle représentant une détection d'activité
 */
public class ActivityDetection {
    
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    @JsonProperty("predicted_activity")
    private String predictedActivity;
    
    @JsonProperty("confidence")
    private double confidence;
    
    @JsonProperty("source")
    private DetectionSource source;
    
    @JsonProperty("person_detected")
    private boolean personDetected;
    
    @JsonProperty("person_confidence")
    private double personConfidence;
    
    @JsonProperty("predictions")
    private Map<String, Double> predictions;
    
    @JsonProperty("fusion_weights")
    private FusionWeights fusionWeights;
    
    public ActivityDetection() {}
    
    public ActivityDetection(String predictedActivity, double confidence, DetectionSource source) {
        this.timestamp = LocalDateTime.now();
        this.predictedActivity = predictedActivity;
        this.confidence = confidence;
        this.source = source;
    }

    // Getters et Setters
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPredictedActivity() {
        return predictedActivity;
    }

    public void setPredictedActivity(String predictedActivity) {
        this.predictedActivity = predictedActivity;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public DetectionSource getSource() {
        return source;
    }

    public void setSource(DetectionSource source) {
        this.source = source;
    }

    public boolean isPersonDetected() {
        return personDetected;
    }

    public void setPersonDetected(boolean personDetected) {
        this.personDetected = personDetected;
    }

    public double getPersonConfidence() {
        return personConfidence;
    }

    public void setPersonConfidence(double personConfidence) {
        this.personConfidence = personConfidence;
    }

    public Map<String, Double> getPredictions() {
        return predictions;
    }

    public void setPredictions(Map<String, Double> predictions) {
        this.predictions = predictions;
    }

    public FusionWeights getFusionWeights() {
        return fusionWeights;
    }

    public void setFusionWeights(FusionWeights fusionWeights) {
        this.fusionWeights = fusionWeights;
    }

    @Override
    public String toString() {
        return String.format("ActivityDetection{timestamp=%s, activity='%s', confidence=%.2f, source=%s, personDetected=%s}", 
                timestamp, predictedActivity, confidence, source, personDetected);
    }
}