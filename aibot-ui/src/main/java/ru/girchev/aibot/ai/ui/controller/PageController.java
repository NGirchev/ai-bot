package ru.girchev.aibot.ai.ui.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Контроллер для отображения страниц UI
 */
@Controller
public class PageController {

    private static final String SESSION_EMAIL_KEY = "userEmail";

    @GetMapping("/")
    public String index(HttpSession session) {
        // Проверяем, авторизован ли пользователь
        String email = (String) session.getAttribute(SESSION_EMAIL_KEY);
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }
        return "redirect:/chat";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/chat")
    public String chat(HttpSession session) {
        // Проверяем, авторизован ли пользователь
        String email = (String) session.getAttribute(SESSION_EMAIL_KEY);
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }
        return "chat";
    }
}

