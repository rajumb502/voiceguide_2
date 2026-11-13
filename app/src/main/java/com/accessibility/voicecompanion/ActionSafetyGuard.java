package com.accessibility.voicecompanion;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ActionSafetyGuard {
    private static final Pattern RISK = Pattern.compile("\\b(pay(?:ment)?|submit|confirm|transfer|delete|remove|proceed|checkout|otp|logout|log out|book now|send|purchase|buy)\\b", Pattern.CASE_INSENSITIVE);
    public record Pending(String targetId, long revision, long navigation, long createdAt, String summary, String command, int refreshes) {
        public Pending(String id, long revision, long navigation, long createdAt, String summary) { this(id, revision, navigation, createdAt, summary, "", 0); }
    }
    private Pending pending;
    public boolean requiresConfirmation(boolean modelFlag, String text) { return modelFlag || RISK.matcher(text).find(); }
    public void queue(Pending action) { pending = action; }
    public boolean hasPending() { return pending != null; }
    public Pending take(String reply, long now, long navigation) {
        Pending result = pending;
        pending = null;
        String normalized = reply.trim().toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "");
        if (!SharingConsent.isYes(normalized) || result == null || now - result.createdAt() > 30000 || result.navigation() != navigation) return null;
        return result;
    }
    public void clear() { pending = null; }
}
