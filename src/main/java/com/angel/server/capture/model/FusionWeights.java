package com.angel.server.capture.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modèle représentant les poids de fusion des prédictions
 */
public class FusionWeights {
    
    @JsonProperty("image_weight")
    private double imageWeight;
    
    @JsonProperty("sound_weight") 
    private double soundWeight;
    
    public FusionWeights() {}
    
    public FusionWeights(double imageWeight, double soundWeight) {
        this.imageWeight = imageWeight;
        this.soundWeight = soundWeight;
    }

    public double getImageWeight() {
        return imageWeight;
    }

    public void setImageWeight(double imageWeight) {
        this.imageWeight = imageWeight;
    }

    public double getSoundWeight() {
        return soundWeight;
    }

    public void setSoundWeight(double soundWeight) {
        this.soundWeight = soundWeight;
    }
}