package com.accessibility.voicecompanion;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SettingsActivityTest {

    private SettingsActivity activity;
    private ShadowActivity shadowActivity;

    @Before
    public void setUp() {
        ShadowApplication shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication());
        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO);

        activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        shadowActivity = Shadows.shadowOf(activity);
    }

    @Test
    public void testStartSetupAndRestrictedSettingsGuidance() {
        // Trigger voice setup command
        activity.command("start setup");

        // First attempt directs to accessibility settings
        activity.command("allow");
        Intent firstIntent = shadowActivity.getNextStartedActivity();
        assertNotNull(firstIntent);
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, firstIntent.getAction());

        // Second attempt on Android 13+ (SDK 33) triggers restricted settings / App Info intent
        activity.command("start setup");
        activity.command("allow");
        Intent secondIntent = shadowActivity.getNextStartedActivity();
        assertNotNull(secondIntent);
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, secondIntent.getAction());
        assertEquals("package:" + activity.getPackageName(), secondIntent.getDataString());
    }

    @Test
    public void testVoiceSettingsToggle() {
        activity.command("turn off tones");
        AssistantSettings settings = new AssistantSettings(activity);
        assertFalse(settings.audioTonesEnabled());

        activity.command("turn on tones");
        assertTrue(settings.audioTonesEnabled());
    }
}
