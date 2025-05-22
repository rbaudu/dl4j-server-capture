package com.angel.server.capture.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Contrôleur pour les pages web de l'interface utilisateur
 */
@Controller
public class WebController {

    /**
     * Page d'accueil avec dashboard
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "DL4J Server Capture - Dashboard");
        return "index";
    }

    /**
     * Page de streaming vidéo en temps réel
     */
    @GetMapping("/stream")
    public String stream(Model model) {
        model.addAttribute("title", "DL4J Server Capture - Live Stream");
        return "stream";
    }

    /**
     * Page d'historique des activités
     */
    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("title", "DL4J Server Capture - History");
        return "history";
    }

    /**
     * Page de configuration
     */
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("title", "DL4J Server Capture - Settings");
        return "settings";
    }

    /**
     * Page de gestion des personnes de référence
     */
    @GetMapping("/persons")
    public String persons(Model model) {
        model.addAttribute("title", "DL4J Server Capture - Person Management");
        return "persons";
    }

    /**
     * Page de statistiques et monitoring
     */
    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("title", "DL4J Server Capture - Statistics");
        return "stats";
    }
}