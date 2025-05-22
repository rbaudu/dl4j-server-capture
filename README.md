# DL4J Server Capture

Serveur de capture vidéo/audio en temps réel avec détection d'activité utilisant DL4J et les modèles pré-entraînés du projet [dl4j-detection-models](https://github.com/rbaudu/dl4j-detection-models).

## Description

Cette application Java/Spring Boot permet de :

- **Capturer** des vidéos depuis :
  - La caméra intégrée à l'ordinateur
  - Des flux RTSP externes (ex: caméras EZVIZ)
  - L'audio du microphone intégré

- **Détecter** une personne spécifique en utilisant le modèle FaceNet du Zoo DL4J

- **Analyser** l'activité de la personne détectée en utilisant :
  - Les modèles d'activité basés sur les images (Standard, VGG16, ResNet)
  - Les modèles d'activité basés sur le son (Standard, Spectrogramme, MFCC)
  - La fusion des prédictions image + audio

- **Fournir** une interface web temps réel pour :
  - Visualiser les flux vidéo et audio
  - Voir les activités détectées en cours et l'historique
  - Contrôler la capture

- **Exposer** une API REST pour l'intégration avec des systèmes externes

## Fonctionnalités

### 🎥 Capture Multi-Source
- Caméra intégrée avec configuration de résolution et FPS
- Support des flux RTSP avec reconnexion automatique
- Capture audio depuis le microphone avec paramètres configurables

### 🧠 Détection d'Activité Intelligente
- **26 classes d'activité** supportées (voir [CLASSES.md](https://github.com/rbaudu/dl4j-detection-models/blob/main/docs/CLASSES.md))
- Détection de personne avec FaceNet
- Fusion des modalités image/son pour une meilleure précision
- Cache des prédictions pour optimiser les performances

### 🌐 Interface Web Temps Réel
- Dashboard avec contrôles de capture
- Streaming vidéo en direct via WebSocket
- Historique des activités détectées
- Gestion des personnes de référence

### 🔌 API REST Complète
- Endpoints pour démarrer/arrêter la capture
- Récupération de l'activité courante
- Accès à l'historique (jour/semaine/mois/période personnalisée)
- Gestion des images de référence pour la détection de personne

### 💾 Persistance Simple
- Sauvegarde automatique en fichiers JSON
- Rotation quotidienne des fichiers d'historique
- Nettoyage automatique selon la politique de rétention

## Architecture

Le projet suit une architecture modulaire avec séparation claire des responsabilités :

```
com.angel.server.capture/
├── config/              # Configuration WebSocket et handlers
├── controller/          # API REST et contrôleurs web
├── model/              # Modèles de données (ActivityDetection, etc.)
└── service/            # Logique métier
    ├── ModelService             # Gestion des modèles DL4J
    ├── VideoCaptureService      # Capture vidéo
    ├── AudioCaptureService      # Capture audio  
    ├── ActivityDetectionService # Détection d'activité
    ├── PersonDetectionService   # Détection de personne
    └── HistoryService          # Gestion de l'historique
```

## Configuration

Toute la configuration est centralisée dans `config/application.properties` :

### Sources de Capture
```properties
# Caméra intégrée
capture.camera.enabled=true
capture.camera.device.id=0
capture.camera.width=640
capture.camera.height=480
capture.camera.fps=15

# Microphone
capture.microphone.enabled=true
capture.microphone.sample.rate=44100

# Flux RTSP
capture.rtsp.enabled=false
capture.rtsp.urls=rtsp://admin:password@192.168.1.100:554/stream1
```

### Modèles DL4J
```properties
# Répertoire des modèles
models.directory=models

# Modèles d'activité (images)
models.activity.image.standard.path=${models.directory}/activity_standard_model.zip
models.activity.image.vgg16.path=${models.directory}/activity_vgg16_model.zip
models.activity.image.resnet.path=${models.directory}/activity_resnet_model.zip

# Modèles d'activité (son)
models.activity.sound.spectrogram.path=${models.directory}/sound_spectrogram_model.zip
models.activity.sound.mfcc.path=${models.directory}/sound_mfcc_model.zip
```

### Détection d'Activité
```properties
# Intervalle de détection (ms)
detection.interval=2000

# Seuils de confiance
detection.confidence.threshold=0.6
models.facenet.confidence.threshold=0.7

# Fusion des prédictions
detection.fusion.enabled=true
detection.fusion.image.weight=0.6
detection.fusion.sound.weight=0.4
```

## Installation et Démarrage

### Prérequis
- Java 11+
- Maven 3.6+
- Caméra et/ou microphone
- Modèles DL4J entraînés (depuis [dl4j-detection-models](https://github.com/rbaudu/dl4j-detection-models))

### Installation
```bash
# Cloner le repository
git clone https://github.com/rbaudu/dl4j-server-capture.git
cd dl4j-server-capture

# Compiler le projet
mvn clean package

# Copier les modèles DL4J dans le répertoire models/
# (depuis votre projet dl4j-detection-models)
mkdir models
cp /path/to/dl4j-detection-models/models/*.zip models/

# Optionnel: Ajouter des images de référence dans faces/
mkdir faces
cp photo_personne.jpg faces/john.jpg
```

### Démarrage
```bash
# Méthode 1: Via Maven
mvn spring-boot:run

# Méthode 2: Via JAR
java -jar target/dl4j-server-capture-1.0-SNAPSHOT.jar

# Méthode 3: Avec configuration personnalisée
java -jar target/dl4j-server-capture-1.0-SNAPSHOT.jar --spring.config.location=file:./config/
```

L'application sera accessible sur http://localhost:8080

## Utilisation

### Interface Web
1. Accéder à http://localhost:8080
2. Utiliser les boutons "Démarrer/Arrêter la Capture"
3. Visualiser les activités détectées en temps réel
4. Consulter l'historique dans l'onglet "Historique"

### API REST

#### Contrôle de la Capture
```bash
# Démarrer la capture
curl -X POST http://localhost:8080/api/v1/capture/start

# Arrêter la capture  
curl -X POST http://localhost:8080/api/v1/capture/stop

# Statut de la capture
curl http://localhost:8080/api/v1/capture/status
```

#### Activité Courante
```bash
# Activité actuellement détectée
curl http://localhost:8080/api/v1/activity/current
```

#### Historique
```bash
# Historique du jour
curl http://localhost:8080/api/v1/history/today

# Historique de la semaine
curl http://localhost:8080/api/v1/history/week

# Historique d'une période
curl "http://localhost:8080/api/v1/history/period?startDate=2025-01-01&endDate=2025-01-31"

# Supprimer l'historique à partir d'une date
curl -X DELETE http://localhost:8080/api/v1/history/from/2025-01-01
```

#### Gestion des Personnes
```bash
# Lister les personnes de référence
curl http://localhost:8080/api/v1/person/reference

# Ajouter une image de référence
curl -X POST -F "name=john" -F "image=@photo.jpg" http://localhost:8080/api/v1/person/reference

# Supprimer une personne de référence
curl -X DELETE http://localhost:8080/api/v1/person/reference/john
```

## Classes d'Activité Supportées

L'application reconnaît **26 classes d'activité** différentes :

| Classe | Français | Description |
|--------|----------|-------------|
| CLEANING | Nettoyer | Activités de nettoyage, ménage |
| CONVERSING | Converser | Communication verbale |
| COOKING | Cuisiner | Préparation de repas |
| DANCING | Danser | Mouvements rythmiques |
| EATING | Manger | Prise de repas |
| SLEEPING | Dormir | État de sommeil |
| USING_SCREEN | Utiliser écran | Utilisation d'ordinateur/tablette |
| WATCHING_TV | Regarder TV | Visionnage de programmes |
| ... | ... | ... |

Voir la liste complète dans [ActivityClass.java](src/main/java/com/angel/server/capture/model/ActivityClass.java).

## Performances et Optimisation

### Configuration Mémoire
```properties
# Optimisation mémoire
memory.max.heap=4g
memory.optimization.enabled=true

# Thread pools
threads.capture.pool.size=4
threads.detection.pool.size=2

# Cache
cache.models.enabled=true
cache.predictions.size=100
cache.predictions.ttl=300
```

### Temps Réel
- Détection toutes les 2 secondes par défaut
- Streaming vidéo à 10 FPS via WebSocket
- Cache des prédictions pour éviter les recalculs

## Développement

### Structure du Projet
```
dl4j-server-capture/
├── config/                    # Configuration centralisée
├── src/main/
│   ├── java/com/angel/server/capture/
│   │   ├── config/           # Configuration Spring/WebSocket
│   │   ├── controller/       # REST API et Web
│   │   ├── model/           # Modèles de données
│   │   └── service/         # Services métier
│   ├── resources/
│   │   ├── static/          # CSS, JS, images
│   │   └── templates/       # Templates Thymeleaf
├── models/                   # Modèles DL4J (non versionnés)
├── faces/                    # Images de référence (non versionnées)
├── history/                  # Historique JSON (non versionnés)
└── logs/                     # Logs d'application (non versionnés)
```

### Tests
```bash
# Exécuter les tests
mvn test

# Tests d'intégration
mvn verify
```

## Dépendances Principales

- **Spring Boot 2.7.18** - Framework web et IoC
- **DL4J 1.0.0-M2.1** - Deep Learning pour Java
- **JavaCV 1.5.9** - Traitement vidéo/audio (OpenCV + FFmpeg)
- **Jackson** - Sérialisation JSON
- **Thymeleaf** - Templates web
- **WebSocket** - Communication temps réel

## Limitations Connues

- Les modèles DL4J doivent être pré-entraînés avec [dl4j-detection-models](https://github.com/rbaudu/dl4j-detection-models)
- La détection de personne nécessite des images de référence de bonne qualité
- Les flux RTSP peuvent avoir des problèmes de latence selon la configuration réseau
- La conversion audio MFCC est simplifiée (pour une version production, utiliser une bibliothèque spécialisée)

## Contribution

1. Fork le projet
2. Créer une branche feature (`git checkout -b feature/AmazingFeature`)
3. Commit les changements (`git commit -m 'Add AmazingFeature'`)
4. Push vers la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

## Licence

Ce projet est sous licence Apache 2.0. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

## Support

Pour les questions et le support :
- Créer une [issue](https://github.com/rbaudu/dl4j-server-capture/issues)
- Consulter la documentation du projet [dl4j-detection-models](https://github.com/rbaudu/dl4j-detection-models)

---

**Note**: Ce projet est conçu pour fonctionner avec les modèles générés par [dl4j-detection-models](https://github.com/rbaudu/dl4j-detection-models). Assurez-vous d'avoir entraîné et exporté vos modèles avant d'utiliser cette application.