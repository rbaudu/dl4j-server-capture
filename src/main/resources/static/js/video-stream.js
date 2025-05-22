// /src/main/resources/static/js/video-stream.js
// Script pour gérer le streaming vidéo WebSocket dans le dashboard

class VideoStreamManager {
    constructor(videoElementId, websocketUrl) {
        this.videoElement = document.getElementById(videoElementId);
        this.placeholderElement = document.getElementById('video-placeholder');
        this.statusElement = document.getElementById('connection-status');
        this.fpsElement = document.getElementById('fps-counter');
        this.websocketUrl = websocketUrl;
        this.websocket = null;
        this.isConnected = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 3000;
        
        // FPS calculation
        this.frameCount = 0;
        this.lastFpsUpdate = Date.now();
        this.currentFps = 0;
        
        // Heartbeat
        this.heartbeatInterval = null;
        this.heartbeatDelay = 30000; // 30 secondes
    }
    
    connect() {
        try {
            this.updateStatus('Connexion...', 'connecting');
            console.log('[VideoStream] Connexion WebSocket à:', this.websocketUrl);
            
            this.websocket = new WebSocket(this.websocketUrl);
            
            this.websocket.onopen = () => {
                console.log('[VideoStream] WebSocket connecté');
                this.isConnected = true;
                this.reconnectAttempts = 0;
                this.updateStatus('Connecté', 'connected');
                this.startHeartbeat();
            };
            
            this.websocket.onmessage = (event) => {
                try {
                    const message = JSON.parse(event.data);
                    this.handleMessage(message);
                } catch (e) {
                    console.debug('[VideoStream] Message non-JSON reçu');
                }
            };
            
            this.websocket.onclose = (event) => {
                console.log('[VideoStream] WebSocket fermé:', event.code, event.reason);
                this.isConnected = false;
                this.updateStatus('Déconnecté', 'disconnected');
                this.hideVideo();
                this.stopHeartbeat();
                
                // Reconnexion automatique seulement si pas fermé volontairement
                if (event.code !== 1000) {
                    this.attemptReconnect();
                }
            };
            
            this.websocket.onerror = (error) => {
                console.error('[VideoStream] Erreur WebSocket:', error);
                this.isConnected = false;
                this.updateStatus('Erreur', 'error');
                this.hideVideo();
            };
            
        } catch (error) {
            console.error('[VideoStream] Erreur création WebSocket:', error);
            this.updateStatus('Erreur de connexion', 'error');
            this.attemptReconnect();
        }
    }
    
    handleMessage(message) {
        switch (message.type) {
            case 'connected':
                console.log('[VideoStream] Bienvenue:', message.message);
                break;
                
            case 'frame':
                this.displayFrame(message.data);
                this.updateFps();
                break;
                
            case 'status':
                console.log('[VideoStream] Statut capture:', message.capturing);
                break;
                
            default:
                console.debug('[VideoStream] Message inconnu:', message.type);
        }
    }
    
    displayFrame(base64Data) {
        if (this.videoElement && base64Data) {
            this.videoElement.src = base64Data;
            this.videoElement.style.display = 'block';
            
            if (this.placeholderElement) {
                this.placeholderElement.style.display = 'none';
            }
        }
    }
    
    hideVideo() {
        if (this.videoElement) {
            this.videoElement.style.display = 'none';
            this.videoElement.src = '';
        }
        
        if (this.placeholderElement) {
            this.placeholderElement.style.display = 'flex';
        }
        
        if (this.fpsElement) {
            this.fpsElement.textContent = '0';
        }
        
        this.frameCount = 0;
        this.currentFps = 0;
    }
    
    updateFps() {
        this.frameCount++;
        const now = Date.now();
        
        if (now - this.lastFpsUpdate >= 1000) {
            this.currentFps = this.frameCount;
            this.frameCount = 0;
            this.lastFpsUpdate = now;
            
            if (this.fpsElement) {
                this.fpsElement.textContent = this.currentFps;
            }
        }
    }
    
    updateStatus(text, className) {
        if (this.statusElement) {
            this.statusElement.textContent = text;
            this.statusElement.className = `status-indicator ${className}`;
        }
    }
    
    startHeartbeat() {
        this.heartbeatInterval = setInterval(() => {
            if (this.isConnected && this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                this.websocket.send('ping');
            }
        }, this.heartbeatDelay);
    }
    
    stopHeartbeat() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }
    }
    
    attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            const delay = this.reconnectDelay * this.reconnectAttempts;
            
            this.updateStatus(`Reconnexion ${this.reconnectAttempts}/${this.maxReconnectAttempts}...`, 'connecting');
            console.log(`[VideoStream] Reconnexion ${this.reconnectAttempts}/${this.maxReconnectAttempts} dans ${delay}ms`);
            
            setTimeout(() => {
                this.connect();
            }, delay);
        } else {
            console.error('[VideoStream] Impossible de se reconnecter après plusieurs tentatives');
            this.updateStatus('Connexion échouée', 'error');
        }
    }
    
    disconnect() {
        console.log('[VideoStream] Déconnexion manuelle');
        this.stopHeartbeat();
        
        if (this.websocket) {
            this.websocket.close(1000, 'Déconnexion manuelle'); // Code 1000 = fermeture normale
            this.websocket = null;
        }
        
        this.isConnected = false;
        this.updateStatus('Déconnecté', 'disconnected');
        this.hideVideo();
        this.reconnectAttempts = this.maxReconnectAttempts; // Empêcher la reconnexion auto
    }
    
    sendMessage(message) {
        if (this.isConnected && this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            this.websocket.send(message);
        } else {
            console.warn('[VideoStream] WebSocket non connecté, impossible d\'envoyer:', message);
        }
    }
    
    startCapture() {
        this.sendMessage('start_capture');
    }
    
    stopCapture() {
        this.sendMessage('stop_capture');
    }
    
    // Getters
    get connected() {
        return this.isConnected;
    }
    
    get fps() {
        return this.currentFps;
    }
}

// Gestionnaire d'interface utilisateur
class DashboardVideoManager {
    constructor() {
        this.streamManager = null;
        this.toggleButton = null;
        this.init();
    }
    
    init() {
        // Attendre que le DOM soit prêt
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => this.setup());
        } else {
            this.setup();
        }
    }
    
    setup() {
        console.log('[Dashboard] Initialisation du gestionnaire vidéo');
        
        // Créer l'URL WebSocket
        const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${window.location.host}/ws/video`;
        
        // Créer le gestionnaire de stream
        this.streamManager = new VideoStreamManager('video-preview', wsUrl);
        
        // Configurer les boutons
        this.setupButtons();
        
        // Démarrer les mises à jour périodiques
        this.startPeriodicUpdates();
        
        // Auto-connexion
        this.streamManager.connect();
        
        // Nettoyage à la fermeture
        window.addEventListener('beforeunload', () => {
            if (this.streamManager) {
                this.streamManager.disconnect();
            }
        });
        
        // Exposition globale pour debugging
        window.videoStream = this.streamManager;
        window.dashboardVideo = this;
    }
    
    setupButtons() {
        // Bouton de contrôle du stream vidéo
        this.toggleButton = document.getElementById('toggle-video-stream');
        if (this.toggleButton) {
            this.toggleButton.addEventListener('click', () => {
                this.toggleVideoStream();
            });
        }
        
        // Boutons de contrôle de capture (existants)
        const startCaptureBtn = document.getElementById('start-capture');
        const stopCaptureBtn = document.getElementById('stop-capture');
        
        if (startCaptureBtn) {
            startCaptureBtn.addEventListener('click', () => {
                this.startCapture();
            });
        }
        
        if (stopCaptureBtn) {
            stopCaptureBtn.addEventListener('click', () => {
                this.stopCapture();
            });
        }
    }
    
    toggleVideoStream() {
        if (!this.streamManager) return;
        
        if (this.streamManager.connected) {
            this.streamManager.disconnect();
            this.updateToggleButton(false);
        } else {
            this.streamManager.connect();
            this.updateToggleButton(true);
        }
    }
    
    updateToggleButton(connecting) {
        if (!this.toggleButton) return;
        
        if (connecting) {
            this.toggleButton.innerHTML = '<i class="fas fa-video-slash me-1"></i>Déconnecter Stream';
            this.toggleButton.className = 'btn btn-warning';
        } else {
            this.toggleButton.innerHTML = '<i class="fas fa-video me-1"></i>Connecter Stream';
            this.toggleButton.className = 'btn btn-info';
        }
    }
    
    startCapture() {
        console.log('[Dashboard] Démarrage de la capture');
        
        // Appel API pour démarrer la capture
        fetch('/api/v1/video/start', { method: 'GET' })
            .then(response => response.json())
            .then(data => {
                console.log('[Dashboard] Réponse démarrage capture:', data);
                if (this.streamManager) {
                    this.streamManager.startCapture();
                }
            })
            .catch(error => {
                console.error('[Dashboard] Erreur démarrage capture:', error);
            });
    }
    
    stopCapture() {
        console.log('[Dashboard] Arrêt de la capture');
        
        // Appel API pour arrêter la capture
        fetch('/api/v1/video/stop', { method: 'GET' })
            .then(response => response.json())
            .then(data => {
                console.log('[Dashboard] Réponse arrêt capture:', data);
                if (this.streamManager) {
                    this.streamManager.stopCapture();
                }
            })
            .catch(error => {
                console.error('[Dashboard] Erreur arrêt capture:', error);
            });
    }
    
    startPeriodicUpdates() {
        // Mise à jour des statistiques toutes les 2 secondes
        setInterval(() => {
            this.updateStats();
        }, 2000);
    }
    
    updateStats() {
        fetch('/api/v1/video/status')
            .then(response => response.json())
            .then(data => {
                // Mettre à jour les éléments d'information
                const activeSourcesElement = document.getElementById('active-sources');
                const resolutionElement = document.getElementById('resolution');
                
                if (activeSourcesElement) {
                    activeSourcesElement.textContent = data.active_sources || 0;
                }
                
                if (resolutionElement) {
                    if (data.capture_active) {
                        resolutionElement.textContent = '640x480';
                    } else {
                        resolutionElement.textContent = '--';
                    }
                }
                
                // Mettre à jour les badges de statut
                this.updateStatusBadges(data);
            })
            .catch(error => {
                console.debug('[Dashboard] Erreur récupération statut:', error);
            });
    }
    
    updateStatusBadges(data) {
        const videoStatus = document.getElementById('video-status');
        const audioStatus = document.getElementById('audio-status');
        const detectionStatus = document.getElementById('detection-status');
        
        if (videoStatus) {
            if (data.capture_active) {
                videoStatus.innerHTML = '<i class="fas fa-video me-1"></i>Vidéo: Active';
                videoStatus.className = 'badge bg-success';
            } else {
                videoStatus.innerHTML = '<i class="fas fa-video me-1"></i>Vidéo: Arrêtée';
                videoStatus.className = 'badge bg-secondary';
            }
        }
        
        // Badges audio et détection à implémenter selon vos besoins
        // Pour l'instant, on les laisse comme ils sont
    }
}

// Initialisation automatique
const dashboardVideoManager = new DashboardVideoManager();