package com.accessibility.voicecompanion;

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
        MacroStore.Step step1 = new MacroStore.Step("CLICK", "button#pay", "Pay Now", "BUTTON", "button", "", "", "", "", "");
        MacroStore.Macro macro = new MacroStore.Macro("pay bill", "https://portal.example.com", Arrays.asList(step1));
        store.saveMacro(macro);

        // Trigger macro replay via voice command
        activity.simulateVoiceInput("run macro pay bill");
        assertTrue(activity.isReplayingMacro());

        // Step execution when page structure changed/element missing falls back smoothly
        activity.executeNextMacroStep();

        // Cancelling stops replay
        activity.simulateVoiceInput("stop");
        assertFalse(activity.isReplayingMacro());
    }
}
