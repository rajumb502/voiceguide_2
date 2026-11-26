package com.accessibility.voicecompanion;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MacroStoreTest {

    private Context context;
    private MacroStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        store = new MacroStore(context);
        store.clear();
    }

    @Test
    public void testSaveAndRetrieveMacro() {
        MacroStore.Step step1 = new MacroStore.Step("TYPE", "input[name='account']", "Account Number", "INPUT", "", "account", "Enter Account", "1234567890", "", "");
        MacroStore.Step step2 = new MacroStore.Step("CLICK", "button#submit", "Check Bill", "BUTTON", "button", "submit", "", "", "", "");
        MacroStore.Macro macro = new MacroStore.Macro("Check BESCOM Bill", "https://bescom.karnataka.gov.in", Arrays.asList(step1, step2));

        store.saveMacro(macro);
        assertTrue(store.hasMacro("Check BESCOM Bill"));
        assertEquals(1, store.size());

        MacroStore.Macro retrieved = store.getMacro("check bescom bill");
        assertNotNull(retrieved);
        assertEquals("Check BESCOM Bill", retrieved.name);
        assertEquals("https://bescom.karnataka.gov.in", retrieved.initialUrl);
        assertEquals(2, retrieved.steps.size());
        assertEquals("TYPE", retrieved.steps.get(0).action);
        assertEquals("1234567890", retrieved.steps.get(0).text);
        assertEquals("CLICK", retrieved.steps.get(1).action);
        assertEquals("Check Bill", retrieved.steps.get(1).label);
    }

    @Test
    public void testEncryptedPersistenceAcrossInstances() {
        MacroStore.Step step1 = new MacroStore.Step("CLICK", "a.pay", "Pay Bill", "A", "link", "", "", "", "", "");
        MacroStore.Macro macro = new MacroStore.Macro("Pay Electricity", "https://portal.example.com", Arrays.asList(step1));
        store.saveMacro(macro);

        // Create new store instance with the same context
        MacroStore newStore = new MacroStore(context);
        assertTrue(newStore.hasMacro("Pay Electricity"));
        MacroStore.Macro retrieved = newStore.getMacro("Pay Electricity");
        assertNotNull(retrieved);
        assertEquals(1, retrieved.steps.size());
        assertEquals("Pay Bill", retrieved.steps.get(0).label);
    }

    @Test
    public void testAriaLabelPersistedAndRoundTripped() {
        // Task 5.7: aria-label stored and survives serialisation round-trip
        MacroStore.Step step = new MacroStore.Step(
                "CLICK", "", "Pay electricity bill", "BUTTON", "", "", "", "Pay electricity bill", "", "", "");
        MacroStore.Macro macro = new MacroStore.Macro("BESCOM aria", "https://bescom.example.com", Arrays.asList(step));
        store.saveMacro(macro);

        MacroStore newStore = new MacroStore(context);
        MacroStore.Macro retrieved = newStore.getMacro("BESCOM aria");
        assertNotNull(retrieved);
        assertEquals(1, retrieved.steps.size());
        assertEquals("Pay electricity bill", retrieved.steps.get(0).ariaLabel);
        // selector is blank — replay must fall through to aria-label anchor
        assertEquals("", retrieved.steps.get(0).selector);
    }

    @Test
    public void testVoiceCommandMatching() {
        MacroStore.Step step = new MacroStore.Step("CLICK", "button", "Go", "BUTTON", "", "", "", "", "", "");
        store.saveMacro(new MacroStore.Macro("Check BESCOM Bill", "https://example.com", Arrays.asList(step)));
        store.saveMacro(new MacroStore.Macro("View Gas Bill", "https://gas.example.com", Arrays.asList(step)));

        // Matching with explicit prefixes
        MacroStore.Macro m1 = store.findMatchingMacro("run macro Check BESCOM Bill");
        assertNotNull(m1);
        assertEquals("Check BESCOM Bill", m1.name);

        MacroStore.Macro m2 = store.findMatchingMacro("replay macro view gas bill");
        assertNotNull(m2);
        assertEquals("View Gas Bill", m2.name);

        MacroStore.Macro m3 = store.findMatchingMacro("macro check bescom bill");
        assertNotNull(m3);
        assertEquals("Check BESCOM Bill", m3.name);

        // Direct command matching without prefix
        MacroStore.Macro m4 = store.findMatchingMacro("check bescom bill");
        assertNotNull(m4);
        assertEquals("Check BESCOM Bill", m4.name);

        // Unknown command
        assertNull(store.findMatchingMacro("what is the weather today"));
        assertNull(store.findMatchingMacro(""));
        assertNull(store.findMatchingMacro(null));
    }

    @Test
    public void testDeleteAndListMacros() {
        MacroStore.Step step = new MacroStore.Step("CLICK", "button", "Submit", "BUTTON", "", "", "", "", "", "");
        store.saveMacro(new MacroStore.Macro("Macro One", "https://one.com", Arrays.asList(step)));
        store.saveMacro(new MacroStore.Macro("Macro Two", "https://two.com", Arrays.asList(step)));

        List<String> names = store.listMacroNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("Macro One"));
        assertTrue(names.contains("Macro Two"));

        boolean deleted = store.deleteMacro("Macro One");
        assertTrue(deleted);
        assertFalse(store.hasMacro("Macro One"));
        assertEquals(1, store.size());

        store.clear();
        assertEquals(0, store.size());
    }
}
