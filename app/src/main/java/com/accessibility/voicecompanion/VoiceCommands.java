package com.accessibility.voicecompanion;

import java.net.URI;
import java.util.Locale;

/** Small offline command vocabulary; other requests are sent to the selected assistant. */
public final class VoiceCommands {
    public static final String HELP = "Tap Speak, wait for the tone, then say your command. "
        + "Try: open a website; read this page; list links; list buttons; scroll down; scroll up; go back; or help. "
        + "With an API key in Settings, tell me a goal such as find an article, search this site, or help fill a form. I will inspect the page and work through the steps. Tap Speak to interrupt, or Stop to cancel. "
        + "For a proposed click, tap Speak again and say yes or no.";
    public static String normalize(String text) { return text.trim().toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "").replaceAll("\\s+", " "); }
    public static String navigationUrl(String text) {
        String value = text.trim().replaceAll("[.!?]+$", "").replaceFirst("(?i)^(?:please )?(?:go to|open|visit|navigate to)\\s+", "");
        value = value.replaceAll("(?i)\\s+dot\\s+", ".").replaceAll("\\s*\\.\\s*", ".");
        if (value.contains(" ")) return null;
        if (!value.contains("://")) value = "https://" + value;
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null || host == null
                    || !host.matches("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) return null;
            return uri.toASCIIString();
        } catch (Exception e) { return null; }
    }
}
