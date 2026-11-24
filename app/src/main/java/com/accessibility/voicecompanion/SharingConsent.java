package com.accessibility.voicecompanion;

import java.net.IDN;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Consent replies are interpreted locally, never by a remote model. */
public final class SharingConsent {
    public enum Status { ALLOWED, DECLINED, UNCLEAR, EXPIRED }
    public record Reply(Status status, String command) {}

    /**
     * Strict Indian banking and UPI apps that can NEVER be shared with remote AI.
     */
    public static final Set<String> STRICT_BANKING_PACKAGES;
    static {
        Set<String> set = new HashSet<>();
        set.add("com.phonepe.app");                         // PhonePe
        set.add("com.google.android.apps.nbu.paisa.user");  // Google Pay (Tez)
        set.add("net.one97.paytm");                         // Paytm
        set.add("com.sbi.lotusintouch");                    // SBI YONO
        set.add("in.org.npci.upiapp");                      // BHIM UPI
        set.add("com.hdfcbank.payzapp");                    // HDFC PayZapp
        set.add("com.snapwork.hdfc");                       // HDFC MobileBanking
        set.add("com.icicibank.mobile");                    // ICICI iMobile
        set.add("com.axis.mobile");                         // Axis Mobile
        STRICT_BANKING_PACKAGES = Collections.unmodifiableSet(set);
    }

    /**
     * Default-blocked sensitive packages: permanently blocked banking/UPI apps.
     */
    public static final Set<String> BLOCKED_PACKAGES = STRICT_BANKING_PACKAGES;

    public static boolean isBankingPackage(String packageName) {
        if (packageName == null) return false;
        String pkg = packageName.trim().toLowerCase(Locale.ROOT);
        return STRICT_BANKING_PACKAGES.contains(pkg);
    }

    public static boolean isPackageBlocked(String packageName) {
        return isBankingPackage(packageName);
    }

    public static String getBankingSecurityWarning(String packageName) {
        if (isBankingPackage(packageName)) {
            return "Banking and payment apps cannot be shared with AI providers for your financial security.";
        }
        return null;
    }

    // Per-provider package allowlists backed by KeystoreCryptoHelper
    private static final Map<String, Set<String>> ALLOWED_PACKAGES = new ConcurrentHashMap<>();
    private static volatile String activeProvider = AssistantSettings.GEMINI;

    public static void setActiveProvider(String provider) {
        if (provider != null && !provider.isEmpty()) activeProvider = provider;
    }

    public static String getActiveProvider() {
        return activeProvider;
    }

    public static boolean isPackageAllowed(String provider, String packageName) {
        if (packageName == null || isBankingPackage(packageName)) return false;
        String pkg = packageName.trim().toLowerCase(Locale.ROOT);
        String prov = (provider != null && !provider.isEmpty()) ? provider : activeProvider;
        Set<String> set = ALLOWED_PACKAGES.get(prov);
        return set != null && set.contains(pkg);
    }

    public static boolean isPackageAllowed(String packageName) {
        return isPackageAllowed(activeProvider, packageName);
    }

    public static void setPackageAllowed(String provider, String packageName, boolean allowed) {
        if (packageName == null) return;
        String pkg = packageName.trim().toLowerCase(Locale.ROOT);
        if (allowed && isBankingPackage(pkg)) {
            throw new SecurityException("Banking and payment apps cannot be shared with AI providers for your financial security: " + pkg);
        }
        String prov = (provider != null && !provider.isEmpty()) ? provider : activeProvider;
        Set<String> set = ALLOWED_PACKAGES.computeIfAbsent(prov, k -> ConcurrentHashMap.newKeySet());
        if (allowed) {
            set.add(pkg);
        } else {
            set.remove(pkg);
        }
        persistAllowlist(prov);
    }

    public static void setPackageAllowed(String packageName, boolean allowed) {
        setPackageAllowed(activeProvider, packageName, allowed);
    }

    public static Set<String> getAllowedPackages(String provider) {
        String prov = (provider != null && !provider.isEmpty()) ? provider : activeProvider;
        Set<String> set = ALLOWED_PACKAGES.get(prov);
        return set != null ? Collections.unmodifiableSet(new TreeSet<>(set)) : Collections.emptySet();
    }

    public static Set<String> getAllowedPackages() {
        return getAllowedPackages(activeProvider);
    }

    public static void clearAllowedPackages() {
        ALLOWED_PACKAGES.clear();
    }

    public static String getAppConsentPrompt(String appName, String provider) {
        String prov = (provider != null && !provider.isEmpty()) ? provider : activeProvider;
        return "Allow sharing screen details from " + appName + " with " + prov + "? Say Yes or No";
    }

    private static void persistAllowlist(String provider) {
        try {
            Set<String> set = ALLOWED_PACKAGES.getOrDefault(provider, Collections.emptySet());
            String raw = String.join(",", set);
            KeystoreCryptoHelper.encrypt("package_allowlist_" + provider, raw);
        } catch (Exception ignored) {}
    }

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
