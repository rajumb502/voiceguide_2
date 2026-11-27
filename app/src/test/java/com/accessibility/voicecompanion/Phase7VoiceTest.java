package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class Phase7VoiceTest {

    private ActivityController<VoiceBrowserActivity> controller;
    private VoiceBrowserActivity activity;
    private ShadowActivity shadowActivity;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(VoiceBrowserActivity.class).create().start().resume();
        activity = controller.get();
        shadowActivity = Shadows.shadowOf(activity);
    }

    @After
    public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test
    public void testUserFactsEncryptionRoundTrip() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        UserPreferences.clearFacts(ctx);

        UserPreferences.putFact(ctx, "consumer ID", "12345");
        UserPreferences.putFact(ctx, "email", "user@example.com");

        // Verify stored value in SharedPreferences is encrypted (does not contain plaintext)
        SharedPreferences rawPrefs = ctx.getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE);
        String ciphertext = rawPrefs.getString(UserPreferences.KEY_FACTS_ENCRYPTED, "");
        assertFalse(ciphertext.isEmpty());
        assertFalse(ciphertext.contains("12345"));
        assertFalse(ciphertext.contains("user@example.com"));

        Map<String, String> loaded = UserPreferences.loadFacts(ctx);
        assertEquals("12345", loaded.get("consumer ID"));
        assertEquals("user@example.com", loaded.get("email"));

        // Overwrite existing fact case-insensitively
        UserPreferences.putFact(ctx, "Consumer ID", "99999");
        loaded = UserPreferences.loadFacts(ctx);
        assertEquals(2, loaded.size());
        assertEquals("99999", loaded.get("Consumer ID"));

        UserPreferences.clearFacts(ctx);
        assertTrue(UserPreferences.loadFacts(ctx).isEmpty());
    }

    @Test
    public void testVoiceBrowserPrivacyTogglesAndFactsCommands() {
        AssistantSettings settings = new AssistantSettings(activity);

        activity.simulateVoiceInput("disable page sharing");
        assertFalse(settings.sharingEnabled());
        activity.simulateVoiceInput("enable page sharing");
        assertTrue(settings.sharingEnabled());

        activity.simulateVoiceInput("disable screenshots");
        assertFalse(settings.screenshotsEnabled());
        activity.simulateVoiceInput("enable screenshots");
        assertTrue(settings.screenshotsEnabled());

        activity.simulateVoiceInput("disable remembering preferences");
        assertFalse(settings.rememberPreferences());
        activity.simulateVoiceInput("remember my consumer ID is 55555");
        assertTrue(UserPreferences.loadFacts(activity).isEmpty());

        activity.simulateVoiceInput("enable remembering preferences");
        assertTrue(settings.rememberPreferences());
        activity.simulateVoiceInput("remember my consumer ID is 55555");
        assertEquals("55555", UserPreferences.loadFacts(activity).get("consumer ID"));

        activity.simulateVoiceInput("what facts do you remember");
        assertEquals("55555", UserPreferences.loadFacts(activity).get("consumer ID"));

        activity.simulateVoiceInput("forget my facts");
        assertTrue(UserPreferences.loadFacts(activity).isEmpty());
    }

    @Test
    public void testStartFreshClearsConversationMemory() throws Exception {
        ConversationMemory memory = new ConversationMemory(activity);
        memory.add("user", "Check my bill", "https://example.com", "Example", 1L);
        assertTrue(memory.snapshot("example.com").length() > 0);
        memory.close();

        activity.simulateVoiceInput("start fresh");

        ConversationMemory verify = new ConversationMemory(activity);
        assertEquals(0, verify.snapshot("example.com").length());
        verify.close();
    }

    @Test
    public void testGeoDeepLinkLaunchesViewIntentImmediately() {
        activity.simulateVoiceInput("navigate to Bangalore City Railway Station");
        Intent intent = shadowActivity.getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("geo:0,0?q=Bangalore%20City%20Railway%20Station", intent.getDataString());
    }

    @Test
    public void testCallDeepLinkRequiresHardwareConfirmation() {
        activity.simulateVoiceInput("call 9876543210");
        assertNull(shadowActivity.getNextStartedActivity());

        // Spoken confirmation must not launch dialer
        activity.simulateVoiceInput("yes");
        assertNull(shadowActivity.getNextStartedActivity());

        // Double-press Volume Up launches ACTION_DIAL
        long now = SystemClock.elapsedRealtime();
        activity.simulateKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0));
        assertNull(shadowActivity.getNextStartedActivity());
        activity.simulateKeyEvent(new KeyEvent(now + 200, now + 200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0));

        Intent intent = shadowActivity.getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_DIAL, intent.getAction());
        assertEquals("tel:9876543210", intent.getDataString());
    }

    @Test
    public void testMailtoDeepLinkCancelledByVolumeDownAndConfirmedByVolumeUp() {
        activity.simulateVoiceInput("email user@example.com");
        assertNull(shadowActivity.getNextStartedActivity());

        long now = SystemClock.elapsedRealtime();
        activity.simulateKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0));
        assertNull(shadowActivity.getNextStartedActivity());

        activity.simulateVoiceInput("email user@example.com");
        now = SystemClock.elapsedRealtime();
        activity.simulateKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0));
        activity.simulateKeyEvent(new KeyEvent(now + 200, now + 200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0));

        Intent intent = shadowActivity.getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("mailto:user@example.com", intent.getDataString());
    }
}
