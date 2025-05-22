// Application principale DL4J Server Capture
class DL4JCaptureApp {
    constructor() {
        this.wsActivity = null;
        this.wsVideo = null;
        this.isConnected = false;
        this.currentActivity = null;
        this.captureStatus = {
            video: false,
            audio: false,
            detection: false
        };
        
        this.init();
    }

    init() {
        this.connectWebSockets();
        this.loadStatus();
        this.setupEventListeners();
        
        // Actualiser le statut toutes les 10 secondes
        setInterval(() => this.loadStatus(), 10000);
    }

    // ========== WEBSOCKETS ==========

    connectWebSockets() {
        this.connectActivityWebSocket();
        this.connectVideoWebSocket();
    }

    connectActivityWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws`;
        
        try {
            this.wsActivity = new WebSocket(wsUrl);
            
            this.wsActivity.onopen = () => {
                console.log('WebSocket activité connecté');
                this.updateConnectionStatus(true);
            };
            
            this.wsActivity.onmessage = (event) => {
                try {
                    const detection = JSON.parse(event.data);
                    this.handleActivityDetection(detection);
                } catch (e) {
                    console.error('Erreur parsing activité:', e);
                }
            };
            
            this.wsActivity.onclose = () => {
                console.log('WebSocket activité fermé');
                this.updateConnectionStatus(false);
                // Reconnexion automatique après 3 secondes
                setTimeout(() => this.connectActivityWebSocket(), 3000);
            };
            
            this.wsActivity.onerror = (error) => {
                console.error('Erreur WebSocket activité:', error);
            };
            
        } catch (error) {
            console.error('Impossible de se connecter au WebSocket activité:', error);
        }
    }

    connectVideoWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/video`;
        
        try {
            this.wsVideo = new WebSocket(wsUrl);
            this.wsVideo.binaryType = 'arraybuffer';
            
            this.wsVideo.onopen = () => {
                console.log('WebSocket vidéo connecté');
            };
            
            this.wsVideo.onmessage = (event) => {
                this.handleVideoFrame(event.data);
            };
            
            this.wsVideo.onclose = () => {
                console.log('WebSocket vidéo fermé');
                // Reconnexion automatique après 3 secondes
                setTimeout(() => this.connectVideoWebSocket(), 3000);
            };
            
            this.wsVideo.onerror = (error) => {
                console.error('Erreur WebSocket vidéo:', error);
            };
            
        } catch (error) {
            console.error('Impossible de se connecter au WebSocket vidéo:', error);
        }
    }

    // ========== GESTION DES ÉVÉNEMENTS ==========

    setupEventListeners() {
        // Boutons de contrôle
        document.getElementById('start-capture')?.addEventListener('click', () => this.startCapture());
        document.getElementById('stop-capture')?.addEventListener('click', () => this.stopCapture());
    }

    handleActivityDetection(detection) {
        this.currentActivity = detection;
        this.updateActivityDisplay(detection);
        
        // Émettre un événement personnalisé
        window.dispatchEvent(new CustomEvent('activityDetected', { detail: detection }));
    }

    handleVideoFrame(arrayBuffer) {
        const canvas = document.getElementById('video-preview');
        if (!canvas) return;
        
        const ctx = canvas.getContext('2d');
        const blob = new Blob([arrayBuffer], { type: 'image/jpeg' });
        const url = URL.createObjectURL(blob);
        
        const img = new Image();
        img.onload = () => {
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            URL.revokeObjectURL(url);
        };
        img.src = url;
    }

    // ========== API CALLS ==========

    async loadStatus() {
        try {
            const response = await fetch('/api/v1/capture/status');
            const status = await response.json();
            
            this.captureStatus = {
                video: status.video_capturing || false,
                audio: status.audio_capturing || false,
                detection: status.activity_detecting || false
            };
            
            this.updateStatusDisplay();
            
        } catch (error) {
            console.error('Erreur lors du chargement du statut:', error);
        }
    }

    async startCapture() {
        try {
            this.setButtonsLoading(true);
            
            const response = await fetch('/api/v1/capture/start', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            const result = await response.json();
            
            if (result.status === 'success') {
                this.showNotification('Capture démarrée avec succès', 'success');
                this.loadStatus();
            } else {
                this.showNotification('Erreur: ' + result.message, 'error');
            }
            
        } catch (error) {
            console.error('Erreur lors du démarrage:', error);
            this.showNotification('Erreur lors du démarrage de la capture', 'error');
        } finally {
            this.setButtonsLoading(false);
        }
    }

    async stopCapture() {
        try {
            this.setButtonsLoading(true);
            
            const response = await fetch('/api/v1/capture/stop', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            const result = await response.json();
            
            if (result.status === 'success') {
                this.showNotification('Capture arrêtée avec succès', 'success');
                this.loadStatus();
            } else {
                this.showNotification('Erreur: ' + result.message, 'error');
            }
            
        } catch (error) {
            console.error('Erreur lors de l\'arrêt:', error);
            this.showNotification('Erreur lors de l\'arrêt de la capture', 'error');
        } finally {
            this.setButtonsLoading(false);
        }
    }

    async getCurrentActivity() {
        try {
            const response = await fetch('/api/v1/activity/current');
            const result = await response.json();
            
            if (result.status === 'success' && result.has_detection) {
                return result.activity;
            }
            
            return null;
            
        } catch (error) {
            console.error('Erreur lors de la récupération de l\'activité courante:', error);
            return null;
        }
    }

    // ========== INTERFACE UTILISATEUR ==========

    updateConnectionStatus(connected) {
        this.isConnected = connected;
        const statusElement = document.getElementById('connection-status');
        
        if (statusElement) {
            if (connected) {
                statusElement.className = 'badge bg-success';
                statusElement.innerHTML = '<i class="fas fa-circle"></i> Connecté';
            } else {
                statusElement.className = 'badge bg-danger';
                statusElement.innerHTML = '<i class="fas fa-circle"></i> Déconnecté';
            }
        }
    }

    updateStatusDisplay() {
        // Mise à jour des badges de statut
        const videoStatus = document.getElementById('video-status');
        const audioStatus = document.getElementById('audio-status');
        const detectionStatus = document.getElementById('detection-status');
        
        if (videoStatus) {
            videoStatus.className = this.captureStatus.video ? 'badge bg-success' : 'badge bg-secondary';
            videoStatus.innerHTML = `<i class="fas fa-video me-1"></i>Vidéo: ${this.captureStatus.video ? 'Active' : 'Arrêtée'}`;
        }
        
        if (audioStatus) {
            audioStatus.className = this.captureStatus.audio ? 'badge bg-success' : 'badge bg-secondary';
            audioStatus.innerHTML = `<i class="fas fa-microphone me-1"></i>Audio: ${this.captureStatus.audio ? 'Active' : 'Arrêtée'}`;
        }
        
        if (detectionStatus) {
            detectionStatus.className = this.captureStatus.detection ? 'badge bg-success' : 'badge bg-secondary';
            detectionStatus.innerHTML = `<i class="fas fa-brain me-1"></i>Détection: ${this.captureStatus.detection ? 'Active' : 'Arrêtée'}`;
        }
        
        // Mise à jour des boutons
        const startBtn = document.getElementById('start-capture');
        const stopBtn = document.getElementById('stop-capture');
        
        const isCapturing = this.captureStatus.video || this.captureStatus.audio || this.captureStatus.detection;
        
        if (startBtn) {
            startBtn.disabled = isCapturing;
        }
        
        if (stopBtn) {
            stopBtn.disabled = !isCapturing;
        }
    }

    updateActivityDisplay(detection) {
        const activityElement = document.getElementById('current-activity');
        const personElement = document.getElementById('person-detection');
        
        if (activityElement) {
            const activityClass = this.getActivityClass(detection.predicted_activity);
            const confidencePercent = Math.round(detection.confidence * 100);
            
            activityElement.innerHTML = `
                <div class="d-flex align-items-center">
                    <div class="activity-icon me-3">
                        <i class="${activityClass.icon} fa-3x text-primary"></i>
                    </div>
                    <div class="flex-grow-1">
                        <h4 class="mb-1">${activityClass.french}</h4>
                        <p class="text-muted mb-2">${activityClass.description}</p>
                        <div class="d-flex align-items-center gap-3">
                            <div class="progress flex-grow-1" style="height: 8px;">
                                <div class="progress-bar" role="progressbar" 
                                     style="width: ${confidencePercent}%" 
                                     aria-valuenow="${confidencePercent}" 
                                     aria-valuemin="0" aria-valuemax="100">
                                </div>
                            </div>
                            <span class="badge bg-primary">${confidencePercent}%</span>
                        </div>
                        <small class="text-muted">
                            <i class="fas fa-clock me-1"></i>
                            ${this.formatTimestamp(detection.timestamp)}
                        </small>
                    </div>
                </div>
            `;
        }
        
        if (personElement && detection.person_detected) {
            const personConfidence = Math.round(detection.person_confidence * 100);
            
            personElement.innerHTML = `
                <div class="text-center text-success">
                    <i class="fas fa-user-check fa-2x mb-2"></i>
                    <p class="mb-1">Personne détectée</p>
                    <span class="badge bg-success">${personConfidence}%</span>
                </div>
            `;
        } else if (personElement) {
            personElement.innerHTML = `
                <div class="text-center text-muted">
                    <i class="fas fa-user-times fa-2x mb-2"></i>
                    <p class="mb-0">Aucune personne détectée</p>
                </div>
            `;
        }
    }

    setButtonsLoading(loading) {
        const startBtn = document.getElementById('start-capture');
        const stopBtn = document.getElementById('stop-capture');
        
        if (startBtn) {
            startBtn.disabled = loading;
            if (loading) {
                startBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Démarrage...';
            } else {
                startBtn.innerHTML = '<i class="fas fa-play me-1"></i>Démarrer la Capture';
            }
        }
        
        if (stopBtn) {
            stopBtn.disabled = loading;
            if (loading) {
                stopBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Arrêt...';
            } else {
                stopBtn.innerHTML = '<i class="fas fa-stop me-1"></i>Arrêter la Capture';
            }
        }
    }

    showNotification(message, type = 'info') {
        // Créer une notification Bootstrap
        const alertClass = {
            'success': 'alert-success',
            'error': 'alert-danger',
            'warning': 'alert-warning',
            'info': 'alert-info'
        }[type] || 'alert-info';
        
        const notification = document.createElement('div');
        notification.className = `alert ${alertClass} alert-dismissible fade show position-fixed`;
        notification.style.cssText = 'top: 20px; right: 20px; z-index: 1050; max-width: 400px;';
        notification.innerHTML = `
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        
        document.body.appendChild(notification);
        
        // Supprimer automatiquement après 5 secondes
        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }
        }, 5000);
    }

    // ========== UTILITAIRES ==========

    getActivityClass(activityName) {
        const activities = {
            'CLEANING': { french: 'Nettoyer', icon: 'fas fa-broom', description: 'Activités de nettoyage, ménage' },
            'CONVERSING': { french: 'Converser', icon: 'fas fa-comments', description: 'Communication verbale entre personnes' },
            'COOKING': { french: 'Cuisiner', icon: 'fas fa-utensils', description: 'Préparation de repas, cuisine' },
            'DANCING': { french: 'Danser', icon: 'fas fa-music', description: 'Mouvements rythmiques, danse' },
            'EATING': { french: 'Manger', icon: 'fas fa-hamburger', description: 'Prise de repas, consommation de nourriture' },
            'FEEDING': { french: 'Nourrir', icon: 'fas fa-dog', description: 'Nourrir des animaux' },
            'GOING_TO_SLEEP': { french: 'Se coucher', icon: 'fas fa-bed', description: 'Préparation au coucher' },
            'IRONING': { french: 'Repasser', icon: 'fas fa-tshirt', description: 'Repassage de vêtements' },
            'KNITTING': { french: 'Tricoter', icon: 'fas fa-cut', description: 'Activités de tricot, couture' },
            'LISTENING_MUSIC': { french: 'Écouter musique', icon: 'fas fa-headphones', description: 'Écoute attentive de musique ou radio' },
            'MOVING': { french: 'Se déplacer', icon: 'fas fa-walking', description: 'Déplacement dans l\'espace' },
            'NEEDING_HELP': { french: 'Besoin d\'aide', icon: 'fas fa-hands-helping', description: 'Situation nécessitant de l\'aide' },
            'PHONING': { french: 'Téléphoner', icon: 'fas fa-phone', description: 'Communication téléphonique' },
            'PLAYING': { french: 'Jouer', icon: 'fas fa-gamepad', description: 'Jeux, divertissement' },
            'PLAYING_MUSIC': { french: 'Jouer musique', icon: 'fas fa-guitar', description: 'Performance musicale avec instrument' },
            'PUTTING_AWAY': { french: 'Ranger', icon: 'fas fa-boxes', description: 'Activités de rangement' },
            'READING': { french: 'Lire', icon: 'fas fa-book', description: 'Lecture de livres, journaux, etc.' },
            'RECEIVING': { french: 'Recevoir', icon: 'fas fa-door-open', description: 'Accueil de visiteurs' },
            'SINGING': { french: 'Chanter', icon: 'fas fa-microphone-alt', description: 'Performance vocale' },
            'SLEEPING': { french: 'Dormir', icon: 'fas fa-moon', description: 'État de sommeil' },
            'USING_SCREEN': { french: 'Utiliser écran', icon: 'fas fa-desktop', description: 'Utilisation d\'appareils électroniques' },
            'WAITING': { french: 'Attendre', icon: 'fas fa-clock', description: 'Attente, inactivité' },
            'WAKING_UP': { french: 'Se lever', icon: 'fas fa-sun', description: 'Sortie du sommeil, réveil' },
            'WASHING': { french: 'Se laver', icon: 'fas fa-shower', description: 'Hygiène personnelle' },
            'WATCHING_TV': { french: 'Regarder TV', icon: 'fas fa-tv', description: 'Visionnage de programmes TV' },
            'WRITING': { french: 'Écrire', icon: 'fas fa-pen', description: 'Écriture manuelle ou sur clavier' }
        };
        
        return activities[activityName] || { 
            french: 'Activité inconnue', 
            icon: 'fas fa-question', 
            description: 'Activité non reconnue' 
        };
    }

    formatTimestamp(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleString('fr-FR', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }
}

// Initialiser l'application
let app;
document.addEventListener('DOMContentLoaded', () => {
    app = new DL4JCaptureApp();
});