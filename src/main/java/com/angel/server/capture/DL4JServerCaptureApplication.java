package com.angel.server.capture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principale de l'application DL4J Server Capture
 * 
 * Application de capture vidéo/audio en temps réel avec détection d'activité
 * utilisant les modèles DL4J pré-entraînés.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@PropertySource("file:config/application.properties")
public class DL4JServerCaptureApplication {

    public static void main(String[] args) {
        // Configuration système pour les performances
        System.setProperty("org.bytedeco.javacpp.maxbytes", "0");
        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
        
        // Démarrage de l'application Spring Boot
        SpringApplication.run(DL4JServerCaptureApplication.class, args);
    }
}