import com.accessibility.voicecompanion.SharingConsent;
public class SharingConsentTest {
    private static void check(boolean result) { if (!result) throw new AssertionError(); }
    public static void main(String[] args) {
        SharingConsent consent = new SharingConsent();
        for (String reply : new String[]{"yes", "Yes, submit", "yes, please", "yes send it", "yes share", "हाँ"}) {
            consent.request("open product",1,100);
            SharingConsent.Reply result = consent.evaluate(reply,1,200);
            check(result.status() == SharingConsent.Status.ALLOWED);
            check("open product".equals(result.command())); check(!consent.pending());
            check(consent.evaluate("yes",1,200).status() == SharingConsent.Status.EXPIRED);
        }
        for (String reply : new String[]{"no", "do not share", "don't share", "no thanks", "cancel", "stop"}) {
            consent.request("open product",1,100);
            check(consent.evaluate(reply,1,200).status() == SharingConsent.Status.DECLINED); check(!consent.pending());
        }
        for (String reply : new String[]{"not yes", "yes but no screenshots", "yesterday", "yes submit payment", "", "I guess"}) {
            consent.request("open product",1,100);
            check(consent.evaluate(reply,1,200).status() == SharingConsent.Status.UNCLEAR); check(consent.pending());
            check("open product".equals(consent.accept("yes",1,300)));
        }
        consent.request("help",1,100); check(consent.evaluate("yes",2,200).status() == SharingConsent.Status.EXPIRED);
        consent.request("help",1,100); check(consent.evaluate("yes",1,60101).status() == SharingConsent.Status.EXPIRED);
        consent.request("help",1,100); consent.clear(); check(consent.accept("yes",1,200) == null);
        check(!consent.allows("example.com","Gemini",1));
        consent.request("help",1,100,"example.com","Gemini");
        check(!consent.allows("example.com","Gemini",1));
        check(consent.evaluate("yes",1,200).status() == SharingConsent.Status.ALLOWED);
        check(consent.allows("example.com","Gemini",1));
        check(!consent.allows("other.example","Gemini",1));
        check(!consent.allows("example.com","OpenAI",1));
        check(!consent.allows("example.com","Gemini",2));
        consent.revoke(); check(!consent.allows("example.com","Gemini",1));
        consent.grant("EXAMPLE.COM.","Gemini",2);
        check(consent.allows("example.com","Gemini",2));
        check(!consent.allows("sub.example.com","Gemini",2));
        check(!consent.allows("example.com.evil.test","Gemini",2));
        check(!consent.allows("example.com","Gemini",3));
        consent.revoke(); check(!consent.allows("example.com","Gemini",2));
        check(SharingConsent.sites("EXAMPLE.COM.\nexample.com\nwww.example.com").size() == 2);
        check(SharingConsent.hostname("bücher.example").equals("xn--bcher-kva.example"));
        check(SharingConsent.hostname("[::1]").equals("[::1]"));
        check(SharingConsent.sites(" \n ").isEmpty());
        for (String bad : new String[]{"", "https://example.com", "example.com/path", "example.com:443", "*.example.com", "a..example", "-example.com", "user@example.com", "[invalid]"}) {
            try { SharingConsent.hostname(bad); throw new AssertionError("Invalid hostname accepted: " + bad); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("Spoken sharing consent tests passed");
    }
}
