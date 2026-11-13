package com.accessibility.voicecompanion;

import java.util.ArrayList;
import java.util.List;

/** Only direct user speech is eligible for saved preferences, never page/model text. */
public final class UserPreferences {
    public record Update(String text, String category) {}
    public static Update feedback(String spoken) {
        String normalized = VoiceCommands.normalize(spoken);
        if (normalized.matches("(?:please )?(?:keep (?:your )?answers short|be brief|be concise|give (?:me )?short answers)")) return new Update("Response detail: brief", "Response detail:");
        if (normalized.matches("(?:please )?(?:give (?:me )?more detail|give detailed answers|explain in detail)")) return new Update("Response detail: detailed", "Response detail:");
        String text = spoken.trim().replaceFirst("(?i)^(?:please )?remember(?: that| this preference| my preference)?[ :,-]+", "");
        if (text.equals(spoken.trim())) {
            if (normalized.startsWith("i prefer ")) text = spoken.trim();
            else text = spoken.trim().replaceFirst("(?i)^(?:from now on|for future conversations)[ ,:]+", "");
        }
        if ((text.equals(spoken.trim()) && !normalized.startsWith("i prefer ")) || text.isEmpty()) return null;
        return new Update(text, "");
    }
    public static String merge(String existing, Update update) {
        List<String> notes = new ArrayList<>();
        for (String line : existing.split("\\n")) {
            String item = line.trim();
            if (item.isEmpty() || item.equalsIgnoreCase(update.text()) || (!update.category().isEmpty() && item.startsWith(update.category()))) continue;
            notes.add(item);
        }
        notes.add(update.text().replaceAll("[\\r\\n]+", " ").substring(0,Math.min(500,update.text().replaceAll("[\\r\\n]+", " ").length())));
        while (notes.size()>20 || String.join("\n",notes).length()>4000) notes.remove(0);
        return String.join("\n",notes);
    }
}
