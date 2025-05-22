package com.angel.server.capture.controller;

import com.angel.server.capture.service.VideoCaptureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@RestController
@RequestMapping("/api/v1/video")
public class VideoStreamController {
    
    private static final Logger logger = LoggerFactory.getLogger(VideoStreamController.class);
    
    @Autowired
    private VideoCaptureService videoCaptureService;
    
    private final Map<String, StreamInfo> activeStreams = new ConcurrentHashMap<>();
    
    /**
     * Classe interne pour gérer les informations de stream
     */
    private static class StreamInfo {
        private final OutputStream outputStream;
        private final AtomicBoolean isActive;
        private final Consumer<BufferedImage> frameListener;
        
        public StreamInfo(OutputStream outputStream, Consumer<BufferedImage> frameListener) {
            this.outputStream = outputStream;
            this.frameListener = frameListener;
            this.isActive = new AtomicBoolean(true);
        }
        
        public OutputStream getOutputStream() { return outputStream; }
        public AtomicBoolean getIsActive() { return isActive; }
        public Consumer<BufferedImage> getFrameListener() { return frameListener; }
    }
    
    /**
     * Endpoint pour le streaming vidéo en multipart
     */
    @GetMapping(value = "/stream", produces = "multipart/x-mixed-replace; boundary=frame")
    public ResponseEntity<StreamingResponseBody> streamVideo() {
        
        StreamingResponseBody stream = outputStream -> {
            String streamId = UUID.randomUUID().toString();
            
            // Créer le listener pour ce stream
            Consumer<BufferedImage> frameListener = frame -> {
                try {
                    StreamInfo streamInfo = activeStreams.get(streamId);
                    if (streamInfo != null && streamInfo.getIsActive().get()) {
                        sendFrameToStream(streamInfo.getOutputStream(), frame);
                    }
                } catch (Exception e) {
                    logger.debug("Erreur lors de l'envoi de frame pour stream {}: {}", 
                                streamId, e.getMessage());
                    // Marquer le stream comme inactif
                    StreamInfo streamInfo = activeStreams.get(streamId);
                    if (streamInfo != null) {
                        streamInfo.getIsActive().set(false);
                    }
                }
            };
            
            // Enregistrer le stream
            StreamInfo streamInfo = new StreamInfo(outputStream, frameListener);
            activeStreams.put(streamId, streamInfo);
            
            try {
                // Ajouter le listener au service de capture vidéo
                videoCaptureService.addFrameListener(frameListener);
                
                logger.info("Nouveau stream vidéo démarré: {}", streamId);
                
                // Garder la connexion ouverte tant que le stream est actif
                while (streamInfo.getIsActive().get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
            } catch (Exception e) {
                logger.error("Erreur dans le streaming vidéo pour {}: {}", streamId, e.getMessage());
            } finally {
                // Nettoyage
                videoCaptureService.removeFrameListener(frameListener);
                activeStreams.remove(streamId);
                logger.info("Stream vidéo fermé: {}", streamId);
            }
        };
        
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("Connection", "close")
                .body(stream);
    }
    
    /**
     * Endpoint pour obtenir une image statique
     */
    @GetMapping(value = "/snapshot", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getSnapshot() {
        try {
            // Pour capturer un snapshot, on utilise une approche simple
            // avec un tableau pour capturer la prochaine frame
            final BufferedImage[] capturedFrame = new BufferedImage[1];
            final AtomicBoolean frameReceived = new AtomicBoolean(false);
            
            Consumer<BufferedImage> snapshotListener = frame -> {
                if (!frameReceived.get()) {
                    capturedFrame[0] = frame;
                    frameReceived.set(true);
                }
            };
            
            videoCaptureService.addFrameListener(snapshotListener);
            
            // Attendre maximum 5 secondes pour une frame
            long startTime = System.currentTimeMillis();
            while (!frameReceived.get() && (System.currentTimeMillis() - startTime) < 5000) {
                Thread.sleep(100);
            }
            
            videoCaptureService.removeFrameListener(snapshotListener);
            
            if (capturedFrame[0] != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(capturedFrame[0], "jpg", baos);
                return ResponseEntity.ok(baos.toByteArray());
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Erreur lors de la capture du snapshot: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Endpoint pour vérifier le statut du streaming
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStreamingStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("active_streams", activeStreams.size());
        status.put("capture_active", videoCaptureService.isCapturing());
        status.put("active_sources", videoCaptureService.getActiveSourcesCount());
        status.put("sources", videoCaptureService.getActiveSources());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Envoie une frame au stream en format multipart
     * Utilise uniquement OutputStream standard sans dépendances servlet
     */
    private void sendFrameToStream(OutputStream outputStream, BufferedImage frame) throws IOException {
        // Convertir l'image en bytes JPEG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(frame, "jpg", baos)) {
            throw new IOException("Impossible d'encoder l'image en JPEG");
        }
        byte[] imageBytes = baos.toByteArray();
        baos.close();
        
        // Construire les headers multipart sous forme de string
        StringBuilder headers = new StringBuilder();
        headers.append("\r\n--frame\r\n");
        headers.append("Content-Type: image/jpeg\r\n");
        headers.append("Content-Length: ").append(imageBytes.length).append("\r\n");
        headers.append("\r\n");
        
        // Envoyer les headers
        outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        
        // Envoyer les données de l'image
        outputStream.write(imageBytes);
        
        // Flush pour envoyer immédiatement
        outputStream.flush();
    }
    
    /**
     * Endpoint pour démarrer la capture si elle n'est pas active
     */
    @GetMapping("/start")
    public ResponseEntity<Map<String, Object>> startCapture() {
        try {
            if (!videoCaptureService.isCapturing()) {
                videoCaptureService.startCapture();
                logger.info("Capture vidéo démarrée via API");
            }
            
            Map<String, Object> response = new ConcurrentHashMap<>();
            response.put("status", "success");
            response.put("capturing", videoCaptureService.isCapturing());
            response.put("active_sources", videoCaptureService.getActiveSourcesCount());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Erreur lors du démarrage de la capture: {}", e.getMessage());
            Map<String, Object> response = new ConcurrentHashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Endpoint pour arrêter la capture
     */
    @GetMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopCapture() {
        try {
            if (videoCaptureService.isCapturing()) {
                videoCaptureService.stopCapture();
                logger.info("Capture vidéo arrêtée via API");
            }
            
            Map<String, Object> response = new ConcurrentHashMap<>();
            response.put("status", "success");
            response.put("capturing", videoCaptureService.isCapturing());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Erreur lors de l'arrêt de la capture: {}", e.getMessage());
            Map<String, Object> response = new ConcurrentHashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}