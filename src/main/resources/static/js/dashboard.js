// Dashboard spécifique
class Dashboard {
    constructor() {
        this.recentActivities = [];
        this.maxRecentActivities = 10;
        this.init();
    }

    init() {
        this.loadRecentActivities();
        this.setupEventListeners();
        
        // Actualiser l'historique récent toutes les 30 secondes
        setInterval(() => this.loadRecentActivities(), 30000);
    }

    setupEventListeners() {
        // Écouter les nouvelles détections d'activité
        window.addEventListener('activityDetected', (event) => {
            this.addRecentActivity(event.detail);
        });
    }

    async loadRecentActivities() {
        try {
            const response = await fetch('/api/v1/history/today');
            const result = await response.json();
            
            if (result.status === 'success') {
                // Prendre les 10 dernières activités
                this.recentActivities = result.detections
                    .slice(-this.maxRecentActivities)
                    .reverse();
                
                this.updateRecentActivitiesDisplay();
            }
            
        } catch (error) {
            console.error('Erreur lors du chargement des activités récentes:', error);
        }
    }

    addRecentActivity(detection) {
        // Ajouter au début de la liste
        this.recentActivities.unshift(detection);
        
        // Limiter le nombre d'activités
        if (this.recentActivities.length > this.maxRecentActivities) {
            this.recentActivities = this.recentActivities.slice(0, this.maxRecentActivities);
        }
        
        this.updateRecentActivitiesDisplay();
    }

    updateRecentActivitiesDisplay() {
        const container = document.getElementById('recent-activities');
        if (!container) return;
        
        if (this.recentActivities.length === 0) {
            container.innerHTML = `
                <div class="text-center text-muted py-3">
                    <i class="fas fa-clock fa-2x mb-2"></i>
                    <p>Aucune activité récente</p>
                </div>
            `;
            return;
        }

        const activitiesHtml = this.recentActivities.map(detection => {
            const activityClass = app.getActivityClass(detection.predicted_activity);
            const confidencePercent = Math.round(detection.confidence * 100);
            const timestamp = app.formatTimestamp(detection.timestamp);
            
            return `
                <div class="activity-item d-flex align-items-center p-3 border-bottom">
                    <div class="activity-icon me-3">
                        <i class="${activityClass.icon} fa-lg text-primary"></i>
                    </div>
                    <div class="flex-grow-1">
                        <div class="d-flex justify-content-between align-items-start">
                            <div>
                                <h6 class="mb-1">${activityClass.french}</h6>
                                <small class="text-muted">${timestamp}</small>
                            </div>
                            <div class="text-end">
                                <span class="badge bg-${this.getConfidenceColor(detection.confidence)} mb-1">
                                    ${confidencePercent}%
                                </span>
                                ${detection.person_detected ? '<br><i class="fas fa-user text-success" title="Personne détectée"></i>' : ''}
                            </div>
                        </div>
                        <div class="source-info">
                            <small class="text-muted">
                                <i class="${this.getSourceIcon(detection.source)} me-1"></i>
                                ${this.getSourceName(detection.source)}
                            </small>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        container.innerHTML = activitiesHtml;
    }

    getConfidenceColor(confidence) {
        if (confidence >= 0.8) return 'success';
        if (confidence >= 0.6) return 'warning';
        return 'danger';
    }

    getSourceIcon(source) {
        const icons = {
            'CAMERA': 'fas fa-video',
            'MICROPHONE': 'fas fa-microphone',
            'RTSP': 'fas fa-broadcast-tower',
            'FUSION': 'fas fa-layer-group'
        };
        return icons[source] || 'fas fa-question';
    }

    getSourceName(source) {
        const names = {
            'CAMERA': 'Caméra',
            'MICROPHONE': 'Microphone',
            'RTSP': 'RTSP',
            'FUSION': 'Fusion'
        };
        return names[source] || 'Inconnu';
    }
}

// Initialiser le dashboard
let dashboard;
document.addEventListener('DOMContentLoaded', () => {
    dashboard = new Dashboard();
});