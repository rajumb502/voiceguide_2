package com.accessibility.voicecompanion;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ActionSafetyGuard {
    public static final long TIMEOUT_MS = 30000;
    public static final long DOUBLE_PRESS_WINDOW_MS = 1200;

    private static final Pattern RISK = Pattern.compile(
        "\\b(pay(?:ment)?|submit|confirm|transfer|delete|remove|proceed|checkout|otp|logout|log out|book now|send|purchase|buy)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public record Pending(String targetId, long revision, long navigation, long createdAt, String summary, String command, int refreshes) {
        public Pending(String id, long revision, long navigation, long createdAt, String summary) {
            this(id, revision, navigation, createdAt, summary, "", 0);
        }
    }

    public enum SpokenResult { ABORT, REJECTED, EXPIRED, NONE }

    private Pending pending;
    private long lastVolumeUpTime = 0;

    public boolean requiresConfirmation(boolean modelFlag, String text) {
        return modelFlag || RISK.matcher(text).find();
    }

    public synchronized void queue(Pending action) {
        pending = action;
        lastVolumeUpTime = 0;
    }

    public synchronized boolean hasPending() {
        return pending != null;
    }

    public synchronized Pending getPending() {
        return pending;
    }

    public synchronized boolean isExpired(long now) {
        return pending != null && (now < pending.createdAt() || now - pending.createdAt() >= TIMEOUT_MS);
    }

    /**
     * Hardware confirmation: two Volume Up presses within 1,200ms execute the action.
     * Returns the pending action upon second press within the window, or null otherwise.
     */
    public synchronized Pending onVolumeUp(long now, long navigation) {
        if (pending == null) return null;
        if (now < pending.createdAt() || now - pending.createdAt() >= TIMEOUT_MS || pending.navigation() != navigation) {
            clear();
            return null;
        }
        if (lastVolumeUpTime > 0 && (now - lastVolumeUpTime) <= DOUBLE_PRESS_WINDOW_MS && now >= lastVolumeUpTime) {
            Pending result = pending;
            clear();
            return result;
        } else {
            lastVolumeUpTime = now;
            return null;
        }
    }

    /**
     * Volume Down cancels any pending action immediately.
     */
    public synchronized boolean onVolumeDown(long now) {
        if (pending != null) {
            clear();
            return true;
        }
        return false;
    }

    /**
     * Evaluates spoken input while an action is pending.
     * High-stakes actions CANNOT be confirmed by voice ("Yes"/"Haan" are explicitly rejected).
     * Spoken cancel/stop/ruko commands abort immediately.
     */
    public synchronized SpokenResult evaluateSpoken(String reply, long now, long navigation) {
        if (pending == null) return SpokenResult.NONE;
        if (now < pending.createdAt() || now - pending.createdAt() >= TIMEOUT_MS || pending.navigation() != navigation) {
            clear();
            return SpokenResult.EXPIRED;
        }
        String normalized = reply.trim().toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "");
        if (normalized.matches(".*\\b(cancel|stop|no|ruko|don't|nahin|nahi|mat karo|chhoro|abort)\\b.*") ||
            normalized.equals("no") || normalized.equals("नहीं") || normalized.equals("नही")) {
            clear();
            return SpokenResult.ABORT;
        }
        return SpokenResult.REJECTED;
    }

    /**
     * Legacy method: spoken confirmation is explicitly rejected and always returns null.
     */
    public synchronized Pending take(String reply, long now, long navigation) {
        // High-stakes actions NEVER execute via spoken confirmation.
        evaluateSpoken(reply, now, navigation);
        return null;
    }

    public synchronized void clear() {
        pending = null;
        lastVolumeUpTime = 0;
    }
}
