package com.angel.server.capture.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Service de capture audio depuis le microphone
 */
@Service
public class AudioCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(AudioCaptureService.class);

    // Configuration depuis application.properties
    @Value("${capture.microphone.enabled}")
    private boolean microphoneEnabled;

    @Value("${capture.microphone.sample.rate}")
    private float sampleRate;

    @Value("${capture.microphone.buffer.size}")
    private int bufferSize;

    @Value("${detection.audio.duration}")
    private int audioDuration; // en secondes

    @Value("${threads.capture.pool.size}")
    private int captureThreadPoolSize;

    // État de la capture
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private final List<Consumer<byte[]>> audioListeners = new ArrayList<>();
    
    // Composants audio
    private TargetDataLine microphone;
    private AudioFormat audioFormat;
    private ScheduledExecutorService captureExecutor;
    private ByteArrayOutputStream audioBuffer;

    /**
     * Démarre la capture audio
     */
    public synchronized void startCapture() {
        if (isCapturing.get()) {
            logger.warn("La capture audio est déjà en cours");
            return;
        }

        if (!microphoneEnabled) {
            logger.info("Capture audio désactivée dans la configuration");
            return;
        }

        logger.info("Démarrage de la capture audio...");

        try {
            // Configurer le format audio
            audioFormat = new AudioFormat(
                sampleRate,     // Sample rate
                16,             // Sample size in bits
                1,              // Channels (mono)
                true,           // Signed
                false           // Little endian
            );

            // Obtenir la ligne de capture
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            
            if (!AudioSystem.isLineSupported(info)) {
                logger.error("Format audio non supporté");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat, bufferSize);
            microphone.start();

            // Initialiser le buffer audio
            audioBuffer = new ByteArrayOutputStream();

            // Créer le pool de threads
            captureExecutor = Executors.newScheduledThreadPool(1);

            // Démarrer la capture continue
            captureExecutor.execute(this::captureAudioLoop);

            // Planifier l'envoi des échantillons audio
            int intervalMs = audioDuration * 1000;
            captureExecutor.scheduleWithFixedDelay(
                this::processAudioBuffer,
                intervalMs, intervalMs, TimeUnit.MILLISECONDS
            );

            isCapturing.set(true);
            logger.info("Capture audio démarrée avec succès");

        } catch (Exception e) {
            logger.error("Erreur lors du démarrage de la capture audio: {}", e.getMessage());
        }
    }

    /**
     * Arrête la capture audio
     */
    public synchronized void stopCapture() {
        if (!isCapturing.get()) {
            logger.warn("La capture audio n'est pas en cours");
            return;
        }

        logger.info("Arrêt de la capture audio...");

        isCapturing.set(false);

        // Arrêter le microphone
        if (microphone != null) {
            microphone.stop();
            microphone.close();
            microphone = null;
        }

        // Arrêter le pool de threads
        if (captureExecutor != null) {
            captureExecutor.shutdown();
            try {
                if (!captureExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    captureExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                captureExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Fermer le buffer
        if (audioBuffer != null) {
            try {
                audioBuffer.close();
            } catch (IOException e) {
                logger.debug("Erreur lors de la fermeture du buffer audio: {}", e.getMessage());
            }
            audioBuffer = null;
        }

        logger.info("Capture audio arrêtée");
    }

    /**
     * Boucle principale de capture audio
     */
    private void captureAudioLoop() {
        byte[] buffer = new byte[bufferSize];
        
        while (isCapturing.get() && microphone != null) {
            try {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    synchronized (audioBuffer) {
                        audioBuffer.write(buffer, 0, bytesRead);
                    }
                }
            } catch (Exception e) {
                logger.debug("Erreur lors de la lecture audio: {}", e.getMessage());
                break;
            }
        }
    }

    /**
     * Traite le buffer audio accumulé
     */
    private void processAudioBuffer() {
        try {
            synchronized (audioBuffer) {
                if (audioBuffer.size() > 0) {
                    byte[] audioData = audioBuffer.toByteArray();
                    audioBuffer.reset();
                    
                    // Notifier tous les listeners
                    notifyAudioListeners(audioData);
                }
            }
        } catch (Exception e) {
            logger.error("Erreur lors du traitement du buffer audio: {}", e.getMessage());
        }
    }

    /**
     * Notifie tous les listeners d'audio
     */
    private void notifyAudioListeners(byte[] audioData) {
        for (Consumer<byte[]> listener : audioListeners) {
            try {
                listener.accept(audioData);
            } catch (Exception e) {
                logger.error("Erreur lors de la notification d'un listener audio: {}", e.getMessage());
            }
        }
    }

    /**
     * Ajoute un listener pour les données audio capturées
     */
    public void addAudioListener(Consumer<byte[]> listener) {
        audioListeners.add(listener);
    }

    /**
     * Supprime un listener d'audio
     */
    public void removeAudioListener(Consumer<byte[]> listener) {
        audioListeners.remove(listener);
    }

    /**
     * Vérifie si la capture audio est en cours
     */
    public boolean isCapturing() {
        return isCapturing.get();
    }

    /**
     * Retourne le format audio utilisé
     */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    /**
     * Convertit les données audio brutes en spectrogramme
     */
    public float[][] convertToSpectrogram(byte[] audioData) {
        // Convertir bytes en float
        float[] samples = new float[audioData.length / 2];
        for (int i = 0; i < samples.length; i++) {
            short sample = (short) ((audioData[i * 2 + 1] << 8) | (audioData[i * 2] & 0xFF));
            samples[i] = sample / 32768.0f;
        }

        // Paramètres pour la FFT
        int windowSize = 1024;
        int hopSize = 512;
        int numFrames = (samples.length - windowSize) / hopSize + 1;
        int numFreqs = windowSize / 2 + 1;

        float[][] spectrogram = new float[numFrames][numFreqs];

        // Fenêtre de Hamming
        float[] window = new float[windowSize];
        for (int i = 0; i < windowSize; i++) {
            window[i] = (float) (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (windowSize - 1)));
        }

        // Calculer le spectrogramme (version simplifiée)
        for (int frame = 0; frame < numFrames; frame++) {
            int start = frame * hopSize;
            
            // Appliquer la fenêtre et calculer la magnitude
            for (int i = 0; i < numFreqs; i++) {
                if (start + i < samples.length) {
                    float windowed = samples[start + i] * window[i];
                    spectrogram[frame][i] = Math.abs(windowed);
                } else {
                    spectrogram[frame][i] = 0;
                }
            }
        }

        return spectrogram;
    }

    /**
     * Convertit les données audio en coefficients MFCC
     */
    public float[] convertToMFCC(byte[] audioData) {
        // Conversion simplifiée vers MFCC
        // Dans une implémentation complète, on utiliserait une bibliothèque spécialisée
        
        float[][] spectrogram = convertToSpectrogram(audioData);
        int numMfccCoeffs = 13;
        float[] mfccFeatures = new float[numMfccCoeffs];
        
        // Calcul simplifié des MFCC (approximation)
        for (int i = 0; i < numMfccCoeffs; i++) {
            float sum = 0;
            for (int frame = 0; frame < spectrogram.length; frame++) {
                for (int freq = 0; freq < spectrogram[frame].length; freq++) {
                    sum += spectrogram[frame][freq] * Math.cos(Math.PI * i * freq / spectrogram[frame].length);
                }
            }
            mfccFeatures[i] = sum / (spectrogram.length * spectrogram[0].length);
        }
        
        return mfccFeatures;
    }

    /**
     * Teste si un microphone est disponible
     */
    public static boolean isMicrophoneAvailable() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retourne la liste des mixers audio disponibles
     */
    public static List<String> getAvailableMixers() {
        List<String> mixers = new ArrayList<>();
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        
        for (Mixer.Info mixerInfo : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] targetLines = mixer.getTargetLineInfo();
            
            if (targetLines.length > 0) {
                mixers.add(mixerInfo.getName() + " - " + mixerInfo.getDescription());
            }
        }
        
        return mixers;
    }

    /**
     * Nettoyage automatique à la fermeture
     */
    @PreDestroy
    public void cleanup() {
        stopCapture();
    }
}