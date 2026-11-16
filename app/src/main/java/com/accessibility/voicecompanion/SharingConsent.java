package com.accessibility.voicecompanion;

import java.net.IDN;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Consent replies are interpreted locally, never by a remote model. */
public final class SharingConsent {
    public enum Status { ALLOWED, DECLINED, UNCLEAR, EXPIRED }
    public record Reply(Status status, String command) {}
    private String command;
    private String domain, provider, allowedDomain, allowedProvider;
    private long allowedNavigation = -1;
    private long navigation, expiresAt;
    public synchronized void request(String command, long navigation, long now) { request(command,navigation,now,null,null); }
    public synchronized void request(String command, long navigation, long now, String domain, String provider) {
        allowedDomain = allowedProvider = null; allowedNavigation = -1;
        this.command = command; this.navigation = navigation; expiresAt = now + 60000;
        this.domain = domain; this.provider = provider;
    }
    public synchronized boolean allows(String domain, String provider, long navigation) {
        try { return hostname(domain).equals(allowedDomain) && provider != null && provider.equals(allowedProvider) && navigation == allowedNavigation; }
        catch (IllegalArgumentException e) { return false; }
    }
    public synchronized boolean isDomainAllowed(String domain) {
        if (domain == null) return false;
        try { return hostname(domain).equals(allowedDomain); }
        catch (IllegalArgumentException e) { return false; }
    }
    public synchronized void grant(String domain, String provider, long navigation) {
        String host = hostname(domain);
        clear(); allowedDomain = host; allowedProvider = provider; allowedNavigation = navigation;
    }
    public synchronized void revoke() { clear(); allowedDomain = allowedProvider = null; allowedNavigation = -1; }
    public synchronized boolean pending() { return command != null; }
    public synchronized Reply evaluate(String reply, long navigation, long now) {
        if (command == null || this.navigation != navigation || now > expiresAt) { clear(); return new Reply(Status.EXPIRED, null); }
        String normalized = normalize(reply);
        if (normalized.matches("no|no thanks|no thank you|cancel|stop|do not share|don't share|नहीं|नही")) {
            clear(); return new Reply(Status.DECLINED, null);
        }
        if (!isYes(reply)) return new Reply(Status.UNCLEAR, null);
        String original = command;
        if (domain != null) grant(domain,provider,navigation); else clear();
        return new Reply(Status.ALLOWED, original);
    }
    public String accept(String reply, long navigation, long now) { return evaluate(reply,navigation,now).command(); }
    private static String normalize(String reply) { return VoiceCommands.normalize(reply).replaceAll("[,;:।]", " ").replaceAll("\\s+", " ").trim(); }
    public static boolean isYes(String reply) {
        return normalize(reply).matches("(?:yes|yeah|haan|han|हाँ|हां)(?: please| submit| send| send it| share| share it| allow| allow sharing| i agree)?|confirm|i agree|allow sharing");
    }
    public synchronized void clear() { command = null; domain = provider = null; }
    public static String hostname(String value) {
        if (value == null) throw new IllegalArgumentException("Enter a hostname such as example.com.");
        value = value.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            try { String host = java.net.URI.create("https://" + value).getHost(); if (host != null) return host.toLowerCase(Locale.ROOT); }
            catch (IllegalArgumentException ignored) { }
            throw new IllegalArgumentException("Enter a valid IPv6 hostname.");
        }
        String host;
        try { host = IDN.toASCII(value,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Enter hostnames only, without URLs, ports, paths, or wildcards."); }
        if (host.endsWith(".")) host = host.substring(0,host.length()-1);
        if (host.isEmpty() || host.length()>253) throw new IllegalArgumentException("Enter a valid hostname such as example.com.");
        return host;
    }
    public static Set<String> sites(String text) {
        Set<String> sites = new TreeSet<>();
        if (!text.trim().isEmpty()) for (String site : text.trim().split("\\s+")) sites.add(hostname(site));
        return sites;
    }
}
