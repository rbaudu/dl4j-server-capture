package com.angel.server.capture.model;

/**
 * Énumération des 26 classes d'activité supportées par les modèles DL4J
 * Basé sur le document CLASSES.md du projet dl4j-detection-models
 */
public enum ActivityClass {
    
    CLEANING(1, "CLEANING", "Nettoyer", "Activités de nettoyage, ménage"),
    CONVERSING(2, "CONVERSING", "Converser, parler", "Communication verbale entre personnes"),
    COOKING(3, "COOKING", "Préparer à manger", "Préparation de repas, cuisine"),
    DANCING(4, "DANCING", "Danser", "Mouvements rythmiques, danse"),
    EATING(5, "EATING", "Manger", "Prise de repas, consommation de nourriture"),
    FEEDING(6, "FEEDING", "Nourrir", "Nourrir des animaux (chien/chat/oiseaux/poissons)"),
    GOING_TO_SLEEP(7, "GOING_TO_SLEEP", "Se coucher", "Préparation au coucher"),
    IRONING(8, "IRONING", "Repasser", "Repassage de vêtements"),
    KNITTING(9, "KNITTING", "Tricoter/coudre", "Activités de tricot, couture"),
    LISTENING_MUSIC(10, "LISTENING_MUSIC", "Ecouter de la musique/radio", "Écoute attentive de musique ou radio"),
    MOVING(11, "MOVING", "Se déplacer", "Déplacement dans l'espace"),
    NEEDING_HELP(12, "NEEDING_HELP", "Avoir besoin d'assistance", "Situation nécessitant de l'aide"),
    PHONING(13, "PHONING", "Téléphoner", "Communication téléphonique"),
    PLAYING(14, "PLAYING", "Jouer", "Jeux, divertissement"),
    PLAYING_MUSIC(15, "PLAYING_MUSIC", "Jouer de la musique", "Performance musicale avec instrument"),
    PUTTING_AWAY(16, "PUTTING_AWAY", "Ranger", "Activités de rangement"),
    READING(17, "READING", "Lire", "Lecture de livres, journaux, etc."),
    RECEIVING(18, "RECEIVING", "Recevoir quelqu'un", "Accueil de visiteurs"),
    SINGING(19, "SINGING", "Chanter", "Performance vocale"),
    SLEEPING(20, "SLEEPING", "Dormir", "État de sommeil"),
    USING_SCREEN(21, "USING_SCREEN", "Utiliser un écran", "Utilisation d'appareils électroniques (PC, laptop, tablet, smartphone)"),
    WAITING(22, "WAITING", "Ne rien faire, s'ennuyer", "Attente, inactivité"),
    WAKING_UP(23, "WAKING_UP", "Se lever", "Sortie du sommeil, réveil"),
    WASHING(24, "WASHING", "Se laver, passer aux toilettes", "Hygiène personnelle"),
    WATCHING_TV(25, "WATCHING_TV", "Regarder la télévision", "Visionnage de programmes TV"),
    WRITING(26, "WRITING", "Ecrire", "Écriture manuelle ou sur clavier"),
    UNKNOWN(0, "UNKNOWN", "Inconnu", "Activité non reconnue");
    
    private final int id;
    private final String englishName;
    private final String frenchName;
    private final String description;
    
    ActivityClass(int id, String englishName, String frenchName, String description) {
        this.id = id;
        this.englishName = englishName;
        this.frenchName = frenchName;
        this.description = description;
    }
    
    public int getId() {
        return id;
    }
    
    public String getEnglishName() {
        return englishName;
    }
    
    public String getFrenchName() {
        return frenchName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Trouve une classe d'activité par son nom anglais
     */
    public static ActivityClass fromEnglishName(String name) {
        for (ActivityClass activity : values()) {
            if (activity.englishName.equalsIgnoreCase(name)) {
                return activity;
            }
        }
        return UNKNOWN;
    }
    
    /**
     * Trouve une classe d'activité par son ID
     */
    public static ActivityClass fromId(int id) {
        for (ActivityClass activity : values()) {
            if (activity.id == id) {
                return activity;
            }
        }
        return UNKNOWN;
    }
    
    /**
     * Retourne toutes les classes supportées par la détection d'images
     */
    public static ActivityClass[] getImageSupportedClasses() {
        return values(); // Toutes les classes sont supportées pour les images
    }
    
    /**
     * Retourne les classes supportées par la détection audio
     * Exclut FEEDING, IRONING, PLAYING, SINGING, UNKNOWN selon le document
     */
    public static ActivityClass[] getAudioSupportedClasses() {
        return new ActivityClass[] {
            CLEANING, CONVERSING, COOKING, DANCING, EATING, GOING_TO_SLEEP,
            KNITTING, LISTENING_MUSIC, MOVING, NEEDING_HELP, PHONING,
            PLAYING_MUSIC, PUTTING_AWAY, READING, RECEIVING, SLEEPING,
            USING_SCREEN, WAITING, WAKING_UP, WASHING, WATCHING_TV, WRITING
        };
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s)", frenchName, englishName);
    }
}