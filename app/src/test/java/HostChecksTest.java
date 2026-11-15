import org.junit.Test;
import com.accessibility.voicecompanion.AgentFlowTest;
import com.accessibility.voicecompanion.AssistantContextTest;
import com.accessibility.voicecompanion.ConversationMemoryTest;
import com.accessibility.voicecompanion.UserPreferencesTest;

public class HostChecksTest {
    @Test public void safety() { SafetyGuardTest.main(new String[0]); }
    @Test public void consent() { SharingConsentTest.main(new String[0]); }
    @Test public void commands() { VoiceCommandsTest.main(new String[0]); }
    @Test public void agent() throws Exception { AgentFlowTest.main(new String[0]); }
    @Test public void context() throws Exception { AssistantContextTest.main(new String[0]); }
    @Test public void memory() throws Exception { ConversationMemoryTest.main(new String[0]); }
    @Test public void preferences() throws Exception { UserPreferencesTest.main(new String[0]); }
}
