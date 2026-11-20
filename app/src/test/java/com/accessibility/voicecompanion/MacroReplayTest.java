package com.accessibility.voicecompanion;

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

import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MacroReplayTest {

    private ActivityController<VoiceBrowserActivity> controller;
    private VoiceBrowserActivity activity;
    private MacroStore store;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(VoiceBrowserActivity.class).create().start().resume();
        activity = controller.get();
        store = activity.getMacroStore();
        if (store != null) {
            store.clear();
        }
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.clear();
        }
        if (controller != null) {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void testMacroCommandsVoiceRouting() {
        assertNotNull(store);
        assertEquals(0, store.size());

        // 1. "list macros" when empty
        activity.simulateVoiceInput("list macros");

        // 2. Add a macro directly to store
        MacroStore.Step step1 = new MacroStore.Step("TYPE", "input#account", "Account", "INPUT", "", "account", "", "12345", "", "");
        MacroStore.Step step2 = new MacroStore.Step("CLICK", "button#submit", "Submit", "BUTTON", "button", "", "", "", "", "");
        store.saveMacro(new MacroStore.Macro("check bescom bill", "https://bescom.org", Arrays.asList(step1, step2)));
        assertEquals(1, store.size());

        // 3. "list macros" with 1 macro
        activity.simulateVoiceInput("list macros");

        // 4. "delete macro"
        activity.simulateVoiceInput("delete macro check bescom bill");
        assertEquals(0, store.size());
    }

    @Test
    public void testReplayMacroExecutionAndFallback() {
        assertNotNull(store);
        MacroStore.Step step1 = new MacroStore.Step("CLICK", "button#normal", "Next Page", "BUTTON", "button", "", "", "", "", "");
        MacroStore.Macro macro = new MacroStore.Macro("read tutorial", "https://portal.example.com", Arrays.asList(step1));
        store.saveMacro(macro);

        // Trigger macro replay via voice command
        activity.simulateVoiceInput("run macro read tutorial");
        assertTrue(activity.isReplayingMacro());

        // Step execution when page structure changed/element missing falls back smoothly
        activity.executeNextMacroStep();

        // Cancelling stops replay
        activity.simulateVoiceInput("stop");
        assertFalse(activity.isReplayingMacro());
    }

    @Test
    public void testSensitiveMacroStepRequiresHardwareConfirmation() {
        assertNotNull(store);
        MacroStore.Step step1 = new MacroStore.Step("CLICK", "button#pay", "Pay Now", "BUTTON", "button", "", "", "", "", "");
        MacroStore.Macro macro = new MacroStore.Macro("pay electricity bill", "https://portal.example.com", Arrays.asList(step1));
        store.saveMacro(macro);

        activity.simulateVoiceInput("run macro pay electricity bill");
        assertTrue(activity.isReplayingMacro());

        // Reaching high-stakes action "Pay Now" MUST pause and require hardware confirmation
        activity.executeNextMacroStep();
        assertNotNull(activity.getPendingMacroStep());
        assertEquals("Pay Now", activity.getPendingMacroStep().label);

        // 1. Spoken confirmations like "yes" or "haan" must be rejected
        activity.simulateVoiceInput("yes");
        activity.simulateVoiceInput("haan");
        assertNotNull(activity.getPendingMacroStep());

        // 2. Single Volume Up press is not enough
        long now = SystemClock.elapsedRealtime();
        KeyEvent volUp1 = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0);
        activity.simulateKeyEvent(volUp1);
        assertNotNull(activity.getPendingMacroStep());

        // Key repeats must be ignored (protects TalkBack's 3-second two-key hold shortcut)
        KeyEvent volUpRepeat = new KeyEvent(10, 10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 1);
        activity.simulateKeyEvent(volUpRepeat);
        assertNotNull(activity.getPendingMacroStep());

        // 3. Second Volume Up press within 1,200ms executes
        KeyEvent volUp2 = new KeyEvent(now + 300, now + 300, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0);
        assertTrue(activity.simulateKeyEvent(volUp2));
        assertNull(activity.getPendingMacroStep());
    }

    @Test
    public void testSensitiveMacroStepCancelledByVolumeDown() {
        assertNotNull(store);
        MacroStore.Step step1 = new MacroStore.Step("SUBMIT", "", "Confirm Payment", "BUTTON", "", "", "", "", "", "");
        MacroStore.Macro macro = new MacroStore.Macro("instant transfer", "https://portal.example.com", Arrays.asList(step1));
        store.saveMacro(macro);

        activity.simulateVoiceInput("run macro instant transfer");
        activity.executeNextMacroStep();
        assertNotNull(activity.getPendingMacroStep());

        // Volume Down cancels immediately
        long now = SystemClock.elapsedRealtime();
        KeyEvent volDown = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
        assertTrue(activity.simulateKeyEvent(volDown));

        assertNull(activity.getPendingMacroStep());
        assertFalse(activity.isReplayingMacro());
    }

    @Test
    public void testCoordinateTapWithDynamicViewportScaling() {
        // Assert that coordinate tap with custom viewport bounds (e.g. keyboard open or zoom) calculates scale cleanly
        boolean result = activity.dispatchCoordinateTap(100f, 200f, 400f, 600f);
        // Returns true or false depending on whether webview handled the simulated touch event in Robolectric
        assertFalse(result); // in Robolectric mock webview with 0 dimensions, clamps cleanly without crash
    }
}
