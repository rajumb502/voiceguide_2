import com.accessibility.voicecompanion.ActionSafetyGuard;

public class SafetyGuardTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        ActionSafetyGuard guard = new ActionSafetyGuard();
        check(guard.requiresConfirmation(false, "Pay ₹1450"));
        check(!guard.requiresConfirmation(false, "display information"));

        // Spoken Yes / Haan must NEVER confirm a pending action
        for (String reply : new String[]{"yes", "हाँ", "Haan", "YES PLEASE", "confirm", "proceed"}) {
            guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
            check(guard.take(reply, 200, 2) == null);
            check(guard.evaluateSpoken(reply, 200, 2) == ActionSafetyGuard.SpokenResult.REJECTED);
            check(guard.hasPending());
        }

        // Spoken Cancel / Ruko / Stop / No aborts immediately
        for (String cancel : new String[]{"cancel", "ruko", "stop", "no", "नहीं"}) {
            guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
            check(guard.evaluateSpoken(cancel, 200, 2) == ActionSafetyGuard.SpokenResult.ABORT);
            check(!guard.hasPending());
        }

        // Hardware Volume Up double-press within 1,200ms confirms
        guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
        check(guard.onVolumeUp(200, 2) == null); // First press
        check(guard.hasPending());
        ActionSafetyGuard.Pending confirmed = guard.onVolumeUp(500, 2); // Second press within 300ms (< 1200ms)
        check(confirmed != null);
        check(confirmed.summary().equals("pay"));
        check(!guard.hasPending());

        // Presses > 1,200ms apart do NOT confirm on the second press (treated as new first press)
        guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
        check(guard.onVolumeUp(200, 2) == null);
        check(guard.onVolumeUp(200 + 1201, 2) == null); // Exceeded 1,200ms window
        check(guard.hasPending());

        // Volume Down aborts immediately
        guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
        check(guard.onVolumeDown(300));
        check(!guard.hasPending());

        // Action expires after 30,000ms
        guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
        check(guard.onVolumeUp(100 + ActionSafetyGuard.TIMEOUT_MS, 2) == null);
        check(!guard.hasPending());

        System.out.println("SafetyGuardTest passed");
    }
}
