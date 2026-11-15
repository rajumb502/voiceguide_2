import com.accessibility.voicecompanion.VoiceCommands;

public class VoiceCommandsTest {
    private static void equal(Object a, Object b) { if (!java.util.Objects.equals(a,b)) throw new AssertionError(a + " != " + b); }
    public static void main(String[] args) {
        equal(VoiceCommands.navigationUrl("go to amazon.com"), "https://amazon.com");
        equal(VoiceCommands.navigationUrl("Open amazon dot com."), "https://amazon.com");
        equal(VoiceCommands.navigationUrl("please visit www.amazon.in"), "https://www.amazon.in");
        equal(VoiceCommands.navigationUrl("go to https://example.com/SomePath?q=Value"), "https://example.com/SomePath?q=Value");
        for (String text : new String[]{"click amazon.com link", "read this page", "help", "javascript:alert(1)", "https://user:password@example.com", "go to amazon", "go to invalid..com"}) equal(VoiceCommands.navigationUrl(text), null);
        equal(VoiceCommands.normalize("  Read This Page!  "), "read this page");
        equal(VoiceCommands.appName("Open app Calculator!"),"calculator");
        equal(VoiceCommands.appName("open example.com"),null);
        System.out.println("Voice navigation command tests passed");
    }
}
