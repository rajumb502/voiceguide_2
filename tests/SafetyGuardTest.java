import com.accessibility.voicecompanion.ActionSafetyGuard;

public class SafetyGuardTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        ActionSafetyGuard guard = new ActionSafetyGuard();
        check(guard.requiresConfirmation(false, "Pay ₹1450"));
        check(!guard.requiresConfirmation(false, "display information"));
        String[] rejected = {"no", "do not confirm", "not yes", "yes but change amount", "okay", ""};
        for (String reply : rejected) {
            guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
            check(guard.take(reply, 200, 2) == null);
            check(!guard.hasPending());
        }
        guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
        check(guard.take("हाँ", 200, 2) != null);
        check(guard.take("yes", 200, 2) == null);
        guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
        check(guard.take("yes", 30101, 2) == null);
        guard.queue(new ActionSafetyGuard.Pending("1", 1, 2, 100, "pay"));
        check(guard.take("yes", 200, 3) == null);
        System.out.println("SafetyGuardTest passed");
    }
}
