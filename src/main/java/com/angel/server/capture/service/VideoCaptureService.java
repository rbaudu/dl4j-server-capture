package com.angel.server.capture.service;

import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Service de capture vidéo gérant les caméras locales et flux RTSP
 */
@Service
public class VideoCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(VideoCaptureService.class);

    // Configuration depuis application.properties
    @Value("${capture.camera.enabled}")
    private boolean cameraEnabled;

    @Value("${capture.camera.device.id}")
    private int cameraDeviceId;

    @Value("${capture.camera.width}")
    private int cameraWidth;

    @Value("${capture.camera.height}")
    private int cameraHeight;

    @Value("${capture.camera.fps}")
    private int cameraFps;

    @Value("${capture.rtsp.enabled}")
    private boolean rtspEnabled;

    @Value("${capture.rtsp.urls}")
    private String[] rtspUrls;

    @Value("${capture.rtsp.timeout}")
    private int rtspTimeout;

    @Value("${capture.rtsp.reconnect.delay}")
    private int rtspReconnectDelay;

    @Value("${threads.capture.pool.size}")
    private int captureThreadPoolSize;

    // État de la capture
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, FrameGrabber> activeGrabbers = new ConcurrentHashMap<>();
    private final List<Consumer<BufferedImage>> frameListeners = new ArrayList<>();
    
    // Thread pool pour la capture
    private ScheduledExecutorService captureExecutor;
    private OpenCVFrameConverter.ToMat matConverter;
    private Java2DFrameConverter java2DConverter;

    /**
     * Démarre la capture vidéo
     */
    public synchronized void startCapture() {
        if (isCapturing.get()) {
            logger.warn("La capture vidéo est déjà en cours");
            return;
        }

        logger.info("Démarrage de la capture vidéo...");
        
        // Initialiser les convertisseurs
        matConverter = new OpenCVFrameConverter.ToMat();
        java2DConverter = new Java2DFrameConverter();
        
        // Créer le pool de threads
        captureExecutor = Executors.newScheduledThreadPool(captureThreadPoolSize);
        
        // Démarrer la caméra locale si activée
        if (cameraEnabled) {
            startLocalCamera();
        }
        
        // Démarrer les flux RTSP si activés
        if (rtspEnabled && rtspUrls != null) {
            for (String rtspUrl : rtspUrls) {
                if (rtspUrl != null && !rtspUrl.trim().isEmpty()) {
                    startRTSPStream(rtspUrl.trim());
                }
            }
        }
        
        isCapturing.set(true);
        logger.info("Capture vidéo démarrée");
    }

    /**
     * Arrête la capture vidéo
     */
    public synchronized void stopCapture() {
        if (!isCapturing.get()) {
            logger.warn("La capture vidéo n'est pas en cours");
            return;
        }

        logger.info("Arrêt de la capture vidéo...");
        
        isCapturing.set(false);
        
        // Arrêter tous les grabbers
        activeGrabbers.forEach((key, grabber) -> {
            try {
                grabber.stop();
                grabber.release();
                logger.info("Grabber {} arrêté", key);
            } catch (Exception e) {
                logger.error("Erreur lors de l'arrêt du grabber {}: {}", key, e.getMessage());
            }
        });
        activeGrabbers.clear();
        
        // Arrêter le pool de threads
        if (captureExecutor != null) {
            captureExecutor.shutdown();
            try {
                if (!captureExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    captureExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                captureExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Capture vidéo arrêtée");
    }

    /**
     * Démarre la capture de la caméra locale
     */
    private void startLocalCamera() {
        try {
            logger.info("Démarrage de la caméra locale (device ID: {})", cameraDeviceId);
            
            OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(cameraDeviceId);
            grabber.setImageWidth(cameraWidth);
            grabber.setImageHeight(cameraHeight);
            grabber.setFrameRate(cameraFps);
            
            grabber.start();
            activeGrabbers.put("local_camera", grabber);
            
            // Planifier la capture des frames
            int captureInterval = 1000 / cameraFps; // milliseconds
            captureExecutor.scheduleWithFixedDelay(
                () -> captureFrame("local_camera", grabber),
                0, captureInterval, TimeUnit.MILLISECONDS
            );
            
            logger.info("Caméra locale démarrée avec succès");
            
        } catch (Exception e) {
            logger.error("Erreur lors du démarrage de la caméra locale: {}", e.getMessage());
        }
    }

    /**
     * Démarre la capture d'un flux RTSP
     */
    private void startRTSPStream(String rtspUrl) {
        try {
            logger.info("Démarrage du flux RTSP: {}", rtspUrl);
            
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspUrl);
            grabber.setOption("rtsp_transport", "tcp");
            grabber.setOption("stimeout", String.valueOf(rtspTimeout * 1000)); // microsecondes
            
            grabber.start();
            String key = "rtsp_" + rtspUrl.hashCode();
            activeGrabbers.put(key, grabber);
            
            // Planifier la capture des frames
            captureExecutor.scheduleWithFixedDelay(
                () -> captureFrame(key, grabber),
                0, 100, TimeUnit.MILLISECONDS // 10 fps pour RTSP
            );
            
            // Planifier la reconnexion automatique
            captureExecutor.scheduleWithFixedDelay(
                () -> checkAndReconnectRTSP(key, rtspUrl),
                rtspReconnectDelay, rtspReconnectDelay, TimeUnit.MILLISECONDS
            );
            
            logger.info("Flux RTSP démarré avec succès: {}", rtspUrl);
            
        } catch (Exception e) {
            logger.error("Erreur lors du démarrage du flux RTSP {}: {}", rtspUrl, e.getMessage());
        }
    }

    /**
     * Capture une frame depuis un grabber
     */
    private void captureFrame(String sourceKey, FrameGrabber grabber) {
        try {
            Frame frame = grabber.grab();
            if (frame != null && frame.image != null) {
                // Convertir en BufferedImage
                BufferedImage bufferedImage = java2DConverter.convert(frame);
                if (bufferedImage != null) {
                    // Notifier tous les listeners
                    notifyFrameListeners(bufferedImage);
                }
            }
        } catch (Exception e) {
            logger.debug("Erreur lors de la capture de frame pour {}: {}", sourceKey, e.getMessage());
        }
    }

    /**
     * Vérifie et reconnecte un flux RTSP si nécessaire
     */
    private void checkAndReconnectRTSP(String key, String rtspUrl) {
        try {
            FrameGrabber grabber = activeGrabbers.get(key);
            if (grabber == null) {
                return;
            }
            
            // Essayer de capturer une frame pour tester la connexion
            Frame testFrame = grabber.grab();
            if (testFrame == null) {
                logger.warn("Connexion RTSP perdue pour {}, tentative de reconnexion...", rtspUrl);
                
                // Arrêter le grabber actuel
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    logger.debug("Erreur lors de l'arrêt du grabber RTSP: {}", e.getMessage());
                }
                
                // Essayer de se reconnecter
                startRTSPStream(rtspUrl);
            }
        } catch (Exception e) {
            logger.debug("Erreur lors de la vérification RTSP pour {}: {}", rtspUrl, e.getMessage());
        }
    }

    /**
     * Notifie tous les listeners de frame
     */
    private void notifyFrameListeners(BufferedImage frame) {
        for (Consumer<BufferedImage> listener : frameListeners) {
            try {
                listener.accept(frame);
            } catch (Exception e) {
                logger.error("Erreur lors de la notification d'un listener de frame: {}", e.getMessage());
            }
        }
    }

    /**
     * Ajoute un listener pour les frames capturées
     */
    public void addFrameListener(Consumer<BufferedImage> listener) {
        frameListeners.add(listener);
    }

    /**
     * Supprime un listener de frames
     */
    public void removeFrameListener(Consumer<BufferedImage> listener) {
        frameListeners.remove(listener);
    }

    /**
     * Vérifie si la capture est en cours
     */
    public boolean isCapturing() {
        return isCapturing.get();
    }

    /**
     * Retourne le nombre de sources actives
     */
    public int getActiveSourcesCount() {
        return activeGrabbers.size();
    }

    /**
     * Retourne les informations sur les sources actives
     */
    public List<String> getActiveSources() {
        return new ArrayList<>(activeGrabbers.keySet());
    }

    /**
     * Nettoyage automatique à la fermeture
     */
    @PreDestroy
    public void cleanup() {
        stopCapture();
    }
}