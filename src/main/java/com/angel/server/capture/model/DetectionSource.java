package com.angel.server.capture.model;

/**
 * Énumération des sources de détection
 */
public enum DetectionSource {
    CAMERA("Caméra"),
    MICROPHONE("Microphone"), 
    RTSP("RTSP"),
    FUSION("Fusion Image/Audio");
    
    private final String displayName;
    
    DetectionSource(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}