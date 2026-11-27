import com.accessibility.voicecompanion.VoiceCommands;

public class VoiceCommandsTest {
    private static void equal(Object a, Object b) { if (!java.util.Objects.equals(a,b)) throw new AssertionError(a + " != " + b); }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        equal(VoiceCommands.navigationUrl("go to amazon.com"), "https://amazon.com");
        equal(VoiceCommands.navigationUrl("Open amazon dot com."), "https://amazon.com");
        equal(VoiceCommands.navigationUrl("please visit www.amazon.in"), "https://www.amazon.in");
        equal(VoiceCommands.navigationUrl("go to https://example.com/SomePath?q=Value"), "https://example.com/SomePath?q=Value");
        for (String text : new String[]{"click amazon.com link", "read this page", "help", "javascript:alert(1)", "https://user:password@example.com", "go to amazon", "go to invalid..com"}) equal(VoiceCommands.navigationUrl(text), null);
        equal(VoiceCommands.normalize("  Read This Page!  "), "read this page");
        equal(VoiceCommands.appName("Open app Calculator!"),"calculator");
        equal(VoiceCommands.appName("open example.com"),null);

        check(VoiceCommands.isSessionReset("start fresh"));
        check(VoiceCommands.isSessionReset("New session!"));
        check(VoiceCommands.isSessionReset("reset assistant"));
        check(VoiceCommands.isSessionReset("clear conversation"));
        check(!VoiceCommands.isSessionReset("open wikipedia.org"));

        VoiceCommands.DeepLink tel = VoiceCommands.deepLink("call +91 98765 43210");
        check(tel != null && "tel".equals(tel.scheme()) && "tel:+919876543210".equals(tel.uri()) && tel.requiresConfirmation());
        VoiceCommands.DeepLink telUri = VoiceCommands.deepLink("tel:112");
        check(telUri != null && "tel:112".equals(telUri.uri()) && telUri.requiresConfirmation());

        VoiceCommands.DeepLink geo = VoiceCommands.deepLink("navigate to Bangalore City Railway Station");
        check(geo != null && "geo".equals(geo.scheme()) && "geo:0,0?q=Bangalore%20City%20Railway%20Station".equals(geo.uri()) && !geo.requiresConfirmation());
        VoiceCommands.DeepLink geoUri = VoiceCommands.deepLink("geo:12.9716,77.5946");
        check(geoUri != null && "geo:12.9716,77.5946".equals(geoUri.uri()) && !geoUri.requiresConfirmation());

        VoiceCommands.DeepLink mail = VoiceCommands.deepLink("email user@example.com");
        check(mail != null && "mailto".equals(mail.scheme()) && "mailto:user@example.com".equals(mail.uri()) && mail.requiresConfirmation());
        VoiceCommands.DeepLink mailUri = VoiceCommands.deepLink("mailto:help@bescom.org");
        check(mailUri != null && "mailto:help@bescom.org".equals(mailUri.uri()) && mailUri.requiresConfirmation());

        equal(VoiceCommands.deepLink("navigate to amazon.com"), null);
        equal(VoiceCommands.deepLink("call mom"), null);
        System.out.println("Voice navigation, session reset, and deep-link command tests passed");
    }
}
