package com.accessibility.voicecompanion;

/** Consent replies are interpreted locally, never by a remote model. */
public final class SharingConsent {
    public enum Status { ALLOWED, DECLINED, UNCLEAR, EXPIRED }
    public record Reply(Status status, String command) {}
    private String command;
    private long navigation, expiresAt;
    public void request(String command, long navigation, long now) { this.command = command; this.navigation = navigation; expiresAt = now + 60000; }
    public boolean pending() { return command != null; }
    public Reply evaluate(String reply, long navigation, long now) {
        if (command == null || this.navigation != navigation || now > expiresAt) { clear(); return new Reply(Status.EXPIRED, null); }
        String normalized = normalize(reply);
        if (normalized.matches("no|no thanks|no thank you|cancel|stop|do not share|don't share|नहीं|नही")) {
            clear(); return new Reply(Status.DECLINED, null);
        }
        if (!isYes(reply)) return new Reply(Status.UNCLEAR, null);
        String original = command; clear(); return new Reply(Status.ALLOWED, original);
    }
    public String accept(String reply, long navigation, long now) { return evaluate(reply,navigation,now).command(); }
    private static String normalize(String reply) { return VoiceCommands.normalize(reply).replaceAll("[,;:।]", " ").replaceAll("\\s+", " ").trim(); }
    public static boolean isYes(String reply) {
        return normalize(reply).matches("(?:yes|yeah|haan|han|हाँ|हां)(?: please| submit| send| send it| share| share it| allow| allow sharing| i agree)?|confirm|i agree|allow sharing");
    }
    public void clear() { command = null; }
}
