package com.accessibility.voicecompanion;

import android.os.SystemClock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ActionSafetyGuardTest {
    private ActionSafetyGuard guard;

    @Before
    public void setUp() {
        guard = new ActionSafetyGuard();
    }

    @Test
    public void requiresConfirmationIdentifiesRiskKeywords() {
        assertTrue(guard.requiresConfirmation(false, "Pay ₹1450 electricity bill"));
        assertTrue(guard.requiresConfirmation(false, "submit form"));
        assertTrue(guard.requiresConfirmation(false, "confirm payment"));
        assertTrue(guard.requiresConfirmation(false, "transfer 500"));
        assertTrue(guard.requiresConfirmation(false, "delete account"));
        assertTrue(guard.requiresConfirmation(false, "proceed to checkout"));
        assertTrue(guard.requiresConfirmation(false, "enter otp"));
        assertTrue(guard.requiresConfirmation(true, "ordinary button"));
        assertFalse(guard.requiresConfirmation(false, "read this page"));
        assertFalse(guard.requiresConfirmation(false, "scroll down"));
    }

    @Test
    public void spokenYesOrHaanNeverExecutesPayment() {
        long now = SystemClock.elapsedRealtime();
        guard.queue(new ActionSafetyGuard.Pending("target_pay", 1, 10, now, "Pay BESCOM bill"));

        String[] voiceConfirmations = {"yes", "Yes, please", "haan", "Haan", "हाँ", "confirm", "proceed", "OK"};
        for (String reply : voiceConfirmations) {
            assertNull("Spoken confirmation must never execute sensitive action", guard.take(reply, now + 100, 10));
            ActionSafetyGuard.SpokenResult result = guard.evaluateSpoken(reply, now + 100, 10);
            assertEquals("Spoken confirmation must be rejected", ActionSafetyGuard.SpokenResult.REJECTED, result);
            assertTrue("Action must remain pending after rejected voice confirmation", guard.hasPending());
        }
    }

    @Test
    public void doubleVolumeUpWithinWindowExecutesAction() {
        long now = SystemClock.elapsedRealtime();
        guard.queue(new ActionSafetyGuard.Pending("target_pay", 1, 10, now, "Pay BESCOM bill"));

        // First press: records timestamp, returns null
        assertNull(guard.onVolumeUp(now + 200, 10));
        assertTrue(guard.hasPending());

        // Second press within 500ms (< 1200ms): executes and returns pending action
        ActionSafetyGuard.Pending executed = guard.onVolumeUp(now + 700, 10);
        assertNotNull("Second Volume Up within 1,200ms must execute action", executed);
        assertEquals("target_pay", executed.targetId());
        assertEquals("Pay BESCOM bill", executed.summary());
        assertFalse("Guard must clear after execution", guard.hasPending());
    }

    @Test
    public void volumeUpPressesExceedingWindowDoNotExecute() {
        long now = SystemClock.elapsedRealtime();
        guard.queue(new ActionSafetyGuard.Pending("target_pay", 1, 10, now, "Pay BESCOM bill"));

        // First press at 200ms
        assertNull(guard.onVolumeUp(now + 200, 10));
        assertTrue(guard.hasPending());

        // Second press 1,500ms later (> 1,200ms window): resets timer as a new first press
        assertNull("Press beyond 1,200ms window must not execute", guard.onVolumeUp(now + 1700, 10));
        assertTrue(guard.hasPending());

        // Third press within 300ms of second press: executes!
        ActionSafetyGuard.Pending executed = guard.onVolumeUp(now + 2000, 10);
        assertNotNull("Subsequent press within 1,200ms must execute", executed);
        assertEquals("target_pay", executed.targetId());
    }

    @Test
    public void actionExpiresAfter30Seconds() {
        long now = SystemClock.elapsedRealtime();
        guard.queue(new ActionSafetyGuard.Pending("target_pay", 1, 10, now, "Pay BESCOM bill"));

        assertTrue(guard.hasPending());
        assertFalse(guard.isExpired(now + 29999));
        assertTrue(guard.isExpired(now + 30001));

        // Volume Up after 30s timeout expires safely
        assertNull("Expired action must not execute via Volume Up", guard.onVolumeUp(now + 30001, 10));
        assertFalse("Guard must be cleared on timeout", guard.hasPending());

        // Re-queue and test spoken expiration evaluation
        guard.queue(new ActionSafetyGuard.Pending("target_pay", 1, 10, now, "Pay BESCOM bill"));
        assertEquals(ActionSafetyGuard.SpokenResult.EXPIRED, guard.evaluateSpoken("anything", now + 30001, 10));
        assertFalse(guard.hasPending());
    }

    @Test
    public void volumeDownCancelsImmediately() {
        long now = SystemClock.elapsedRealtime();
        guard.queue(new ActionSafetyGuard.Pending("target_pay", 1, 10, now, "Pay BESCOM bill"));

        assertTrue(guard.onVolumeDown(now + 500));
        assertFalse("Guard must be cleared after Volume Down", guard.hasPending());
    }

    @Test
    public void spokenCancelOrRukoAbortsImmediately() {
        String[] abortCommands = {"cancel", "ruko", "stop", "no", "नहीं", "don't do it"};
        for (String command : abortCommands) {
            long now = SystemClock.elapsedRealtime();
            guard.queue(new ActionSafetyGuard.Pending("target_pay", 1, 10, now, "Pay BESCOM bill"));
            assertEquals("Spoken abort command must return ABORT", ActionSafetyGuard.SpokenResult.ABORT, guard.evaluateSpoken(command, now + 100, 10));
            assertFalse("Guard must be cleared on abort", guard.hasPending());
        }
    }
}
