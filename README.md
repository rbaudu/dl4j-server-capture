# DL4J Server Capture

Serveur de capture vidéo/audio en temps réel avec détection d'activité utilisant DL4J et les modèles pré-entraînés du projet [dl4j-detection-models](https://github.com/rbaudu/dl4j-detection-models).

## Description

Cette application Java/Spring Boot permet de :

- **Capturer** des vidéos depuis :
  - La caméra intégrée à l'ordinateur
  - Des flux RTSP externes (ex: caméras EZVIZ)
  - L'audio du microphone intégré

- **Détecter** la présence d'une personne en utilisant :
  - **Modèles de présence** (Class0/Class1) : `presence_standard_model.zip` et `presence_yolo_model.zip`
  - **FaceNet** (reconnaissance faciale spécifique - optionnel)

- **Analyser** l'activité de la personne **seulement si une présence est détectée** en utilisant :
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

### 👤 Détection de Présence Intelligente
- **Modèles de présence** : Class0 (absence) / Class1 (présence)
- Support des modèles `presence_standard_model.zip` et `presence_yolo_model.zip`
- **FaceNet** optionnel pour reconnaissance faciale spécifique
- Configuration flexible du type de détection

### 🧠 Détection d'Activité Conditionnelle
- **26 classes d'activité** supportées (voir [CLASSES.md](https://github.com/rbaudu/dl4j-detection-models/blob/main/docs/CLASSES.md))
- **Détection d'activité uniquement si une personne est présente**
- Fusion des modalités image/son pour une meilleure précision
- Cache des prédictions pour optimiser les performances

### 🌐 Interface Web Temps Réel
- Dashboard avec contrôles de capture
- Streaming vidéo en direct via WebSocket
- Historique des activités détectées
- Indicateur de présence en temps réel

### 🔌 API REST Complète
- Endpoints pour démarrer/arrêter la capture
- Récupération de l'activité courante
- Accès à l'historique (jour/semaine/mois/période personnalisée)
- Statistiques de détection de présence

### 💾 Persistance Simple
- Sauvegarde automatique en fichiers JSON
- Rotation quotidienne des fichiers d'historique
- Nettoyage automatique selon la politique de rétention

## Configuration de la Détection de Présence

### Types de Détection Supportés

```properties
# Type de détection de personne: 'presence', 'facenet' ou 'disabled'
person.detection.type=presence

# Modèles de présence (Class0=absence, Class1=présence)
models.presence.standard.path=${models.directory}/presence_standard_model.zip
models.presence.yolo.path=${models.directory}/presence_yolo_model.zip
models.presence.default=standard
models.presence.confidence.threshold=0.6

# Modèle FaceNet (reconnaissance faciale - optionnel)
models.facenet.enabled=false
models.facenet.confidence.threshold=0.7
models.facenet.faces.directory=faces

# Nécessiter une présence pour faire la détection d'activité
detection.require.person.presence=true
```

### Modes de Fonctionnement

#### 1. **Mode Présence** (Recommandé)
```properties
person.detection.type=presence
detection.require.person.presence=true
```
- Utilise vos modèles `presence_standard_model.zip` ou `presence_yolo_model.zip`
- Détecte Class0 (absence) vs Class1 (présence)
- Simple et efficace

#### 2. **Mode FaceNet** (Reconnaissance faciale)
```properties
person.detection.type=facenet
models.facenet.enabled=true
detection.require.person.presence=true
```
- Reconnaît des personnes spécifiques
- Nécessite des photos de référence dans le dossier `faces/`

#### 3. **Mode Désactivé**
```properties
person.detection.type=disabled
detection.require.person.presence=false
```
- Détection d'activité continue sans vérification de présence

## Installation et Démarrage

### Prérequis
- Java 11+
- Maven 3.6+
- Caméra et/ou microphone
- **Modèles DL4J entraînés** (depuis [dl4j-detection-models](https://github.com/rbaudu/dl4j-detection-models))

### Installation
```bash
# Cloner le repository
git clone https://github.com/rbaudu/dl4j-server-capture.git
cd dl4j-server-capture

# Compiler le projet
mvn clean package

# Copier vos modèles DL4J dans le répertoire models/
mkdir models
cp /path/to/dl4j-detection-models/models/activity_*.zip models/
cp /path/to/dl4j-detection-models/models/sound_*.zip models/
cp /path/to/dl4j-detection-models/models/presence_*.zip models/
```

### Structure des Modèles Attendue
```
models/
├── activity_standard_model.zip    # Modèle d'activité standard (images)
├── activity_vgg16_model.zip       # Modèle d'activité VGG16 (images)
├── activity_resnet_model.zip      # Modèle d'activité ResNet (images)
├── sound_standard_model.zip       # Modèle de son standard
├── sound_spectrogram_model.zip    # Modèle de son spectrogramme
├── sound_mfcc_model.zip           # Modèle de son MFCC
├── presence_standard_model.zip    # Modèle de présence standard ⭐
└── presence_yolo_model.zip        # Modèle de présence YOLO ⭐
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
3. **Observer l'indicateur de présence** en temps réel
4. Visualiser les activités détectées **seulement quand une personne est présente**
5. Consulter l'historique dans l'onglet "Historique"

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
# Activité actuellement détectée (seulement si présence)
curl http://localhost:8080/api/v1/activity/current
```

#### Statistiques de Présence
```bash
# Statistiques générales (incluent les stats de présence)
curl http://localhost:8080/api/v1/stats
```

## Logique de Fonctionnement

### Flux de Détection

1. **Capture de Frame** → Caméra/RTSP capture une image
2. **Détection de Présence** → Le modèle `presence_*.zip` vérifie si Class1 (présence) > seuil
3. **Condition** :
   - ✅ **Si présence détectée** → Continuer avec la détection d'activité
   - ❌ **Si aucune présence** → Ignorer, attendre la prochaine frame
4. **Détection d'Activité** → Analyser l'activité avec les modèles d'activité
5. **Fusion** (optionnel) → Combiner image + son
6. **Résultat** → Envoyer la détection via WebSocket et sauvegarder

### Avantages de cette Approche

✅ **Économie de ressources** : Pas de détection d'activité inutile quand personne n'est là  
✅ **Précision améliorée** : Évite les faux positifs sur des objets/animaux  
✅ **Historique pertinent** : Seulement les activités humaines réelles  
✅ **Flexibilité** : Possibilité de basculer entre différents modes  

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

## Exemple de Configuration Complète

```properties
# Configuration recommandée pour la détection de présence
person.detection.type=presence
models.presence.default=standard
models.presence.confidence.threshold=0.6
detection.require.person.presence=true

# Modèles d'activité
models.activity.image.default=standard
models.activity.sound.default=spectrogram

# Détection et fusion
detection.interval=2000
detection.confidence.threshold=0.6
detection.fusion.enabled=true
detection.fusion.image.weight=0.6
detection.fusion.sound.weight=0.4
```

## Développement et Tests

### Test de la Détection de Présence
```bash
# Vérifier les modèles disponibles
curl http://localhost:8080/api/v1/stats | jq '.model_stats.model_availability'

# Observer les logs de détection
tail -f logs/dl4j-server-capture.log | grep -i "presence\|personne"
```

### Debug de la Détection
```properties
# Activer les logs debug
logging.level.com.angel.server.capture.service.PresenceDetectionService=DEBUG
logging.level.com.angel.server.capture.service.ActivityDetectionService=DEBUG
```

## Contribution

1. Fork le projet
2. Créer une branche feature (`git checkout -b feature/AmazingFeature`)
3. Commit les changements (`git commit -m 'Add AmazingFeature'`)
4. Push vers la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

## Licence

Ce projet est sous licence Apache 2.0. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

---

**Note Importante**: Cette application est optimisée pour fonctionner avec vos modèles de présence `presence_standard_model.zip` et `presence_yolo_model.zip`. Elle ne fera la détection d'activité que si une personne est effectivement présente, ce qui améliore les performances et la pertinence des résultats ! 🎯