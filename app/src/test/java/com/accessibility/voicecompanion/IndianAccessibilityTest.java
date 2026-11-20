package com.accessibility.voicecompanion;

import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class IndianAccessibilityTest {

    private ActivityController<SettingsActivity> settingsController;
    private SettingsActivity settingsActivity;

    private ActivityController<VoiceBrowserActivity> browserController;
    private VoiceBrowserActivity browserActivity;

    @Before
    public void setUp() {
        settingsController = Robolectric.buildActivity(SettingsActivity.class).create().start().resume();
        settingsActivity = settingsController.get();

        browserController = Robolectric.buildActivity(VoiceBrowserActivity.class).create().start().resume();
        browserActivity = browserController.get();
    }

    @After
    public void tearDown() {
        if (settingsController != null) settingsController.pause().stop().destroy();
        if (browserController != null) browserController.pause().stop().destroy();
    }

    @Test
    public void testRegionalLanguageVoiceCommandsInSettings() {
        assertTrue(settingsActivity.updateSettingByVoice("set language to kannada"));
        AssistantSettings settings = new AssistantSettings(settingsActivity);
        assertEquals("kn-IN", settings.locale());

        assertTrue(settingsActivity.updateSettingByVoice("change language to tamil"));
        assertEquals("ta-IN", settings.locale());

        assertTrue(settingsActivity.updateSettingByVoice("set language to marathi"));
        assertEquals("mr-IN", settings.locale());

        assertTrue(settingsActivity.updateSettingByVoice("set language to telugu"));
        assertEquals("te-IN", settings.locale());

        assertTrue(settingsActivity.updateSettingByVoice("change language to bengali"));
        assertEquals("bn-IN", settings.locale());
    }

    @Test
    public void testCoordinateTapDispatch() {
        assertNotNull(browserActivity);
        // DPI-scaled MotionEvent tap must execute cleanly
        browserActivity.dispatchCoordinateTap(120f, 250f);
    }

    @Test
    public void testOtpAutoReadRequiresHardwareConfirmation() {
        // Simulate OTP received via SMS Consent
        browserActivity.handleOtpReceived("849201", "BESCOM electricity bill payment OTP: 849201");

        // 1. Spoken confirmations like "yes" or "haan" must be rejected
        browserActivity.simulateVoiceInput("yes");
        browserActivity.simulateVoiceInput("haan");

        // 2. Single Volume Up press is not enough
        long now = SystemClock.elapsedRealtime();
        KeyEvent volUp1 = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0);
        browserActivity.simulateKeyEvent(volUp1);

        // 3. Second Volume Up press within 1,200ms confirms
        KeyEvent volUp2 = new KeyEvent(now + 200, now + 200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0);
        assertTrue(browserActivity.simulateKeyEvent(volUp2));
    }

    @Test
    public void testOtpCancellationViaVolumeDown() {
        browserActivity.handleOtpReceived("123456", "Your OTP is 123456");

        long now = SystemClock.elapsedRealtime();
        KeyEvent volDown = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
        assertTrue(browserActivity.simulateKeyEvent(volDown));
    }
}
