package com.accessibility.voicecompanion;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small offline command vocabulary; other requests are sent to the selected assistant. */
public final class VoiceCommands {
    public static final String HELP = "Tap Speak, wait for the tone, then say your command. "
        + "Try: open a website; read this page; list links; list buttons; scroll down; scroll up; go back; open app followed by its name; or help. "
        + "With an API key in Settings, tell me a goal such as find an article, search this site, or help fill a form. I will inspect the page and work through the steps. Tap Speak to interrupt, or Stop to cancel. "
        + "For a proposed click, tap Speak again and say yes or no.";

    private static final Pattern TEL_URI = Pattern.compile("^(?:please\\s+)?(?:open\\s+)?tel:([+0-9][0-9\\s\\-()]{2,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEL_SPOKEN = Pattern.compile("^(?:please\\s+)?(?:call|dial|phone)(?:\\s+number)?\\s+([+0-9][0-9\\s\\-()]{2,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAILTO_URI = Pattern.compile("^(?:please\\s+)?(?:open\\s+)?mailto:([^\\s]+@[^\\s]+\\.[^\\s]{2,}(?:\\?.*)?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAILTO_SPOKEN = Pattern.compile("^(?:please\\s+)?(?:send\\s+(?:an?\\s+)?(?:e-?mail|mail)\\s+to|e-?mail|mail)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_ADDR = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern GEO_URI = Pattern.compile("^(?:please\\s+)?(?:open\\s+)?(geo:[^\\s].*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEO_SPOKEN = Pattern.compile("^(?:please\\s+)?(?:navigate\\s+to|directions\\s+to|find\\s+route\\s+to|route\\s+to|show\\s+map\\s+of|map\\s+of)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    public record DeepLink(String scheme, String uri, String label, boolean requiresConfirmation) {}

    public static String normalize(String text) { return text.trim().toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "").replaceAll("\\s+", " "); }
    public static String appName(String text) {
        String normalized = normalize(text);
        return normalized.startsWith("open app ") ? normalized.substring(9).trim() : null;
    }
    public static boolean isSessionReset(String text) {
        String normalized = normalize(text);
        return normalized.matches("(?:please )?(?:start fresh|new session|reset assistant|start a new session|start over|clear conversation|forget conversation)");
    }
    public static DeepLink deepLink(String text) {
        if (text == null) return null;
        String trimmed = text.trim().replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
        if (trimmed.isEmpty() || navigationUrl(trimmed) != null) return null;

        Matcher m = TEL_URI.matcher(trimmed);
        if (!m.matches()) m = TEL_SPOKEN.matcher(trimmed);
        if (m.matches()) {
            String raw = m.group(1).trim();
            String sanitized = (raw.startsWith("+") ? "+" : "") + raw.replaceAll("[^0-9]", "");
            if (sanitized.replace("+", "").length() >= 3) {
                return new DeepLink("tel", "tel:" + sanitized, sanitized, true);
            }
        }

        m = MAILTO_URI.matcher(trimmed);
        if (m.matches()) {
            String addr = m.group(1).trim();
            return new DeepLink("mailto", "mailto:" + addr, addr, true);
        }
        m = MAILTO_SPOKEN.matcher(trimmed);
        if (m.matches()) {
            String candidate = m.group(1).trim()
                .replaceAll("(?i)\\s+at\\s+", "@")
                .replaceAll("(?i)\\s+dot\\s+", ".");
            if (EMAIL_ADDR.matcher(candidate).matches()) {
                return new DeepLink("mailto", "mailto:" + candidate, candidate, true);
            }
        }

        m = GEO_URI.matcher(trimmed);
        if (m.matches()) {
            String uri = m.group(1).trim();
            return new DeepLink("geo", uri, uri, false);
        }
        m = GEO_SPOKEN.matcher(trimmed);
        if (m.matches()) {
            String place = m.group(1).trim();
            if (!place.isEmpty()) {
                String encoded = URLEncoder.encode(place, StandardCharsets.UTF_8).replace("+", "%20");
                return new DeepLink("geo", "geo:0,0?q=" + encoded, place, false);
            }
        }
        return null;
    }
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
