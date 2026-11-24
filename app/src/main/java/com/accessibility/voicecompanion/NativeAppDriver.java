package com.accessibility.voicecompanion;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.regex.Pattern;

/**
 * On-Demand Native App UI Tree Driver mirroring page.js capabilities for native Android apps.
 * Inspects AccessibilityNodeInfo hierarchy, masks sensitive fields, and dispatches actions.
 */
public class NativeAppDriver {

    public static final int MAX_NODES = 150;

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "(?i).*(otp|pin|mpin|cvv|aadhaar|pan|password|secret).*"
    );

    public record ActionResult(boolean success, String message) {}

    public static boolean isSensitiveNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isPassword()) return true;

        CharSequence text = node.getText();
        if (text != null && SENSITIVE_PATTERN.matcher(text).matches()) return true;

        CharSequence desc = node.getContentDescription();
        if (desc != null && SENSITIVE_PATTERN.matcher(desc).matches()) return true;

        CharSequence viewId = node.getViewIdResourceName();
        if (viewId != null && SENSITIVE_PATTERN.matcher(viewId).matches()) return true;

        return false;
    }

    public static JSONObject captureSnapshot(AccessibilityService service) throws JSONException {
        if (service == null) {
            JSONObject err = new JSONObject();
            err.put("error", "Service not available");
            err.put("nodes", new JSONArray());
            return err;
        }

        AccessibilityNodeInfo root = null;
        try {
            root = service.getRootInActiveWindow();
        } catch (Exception ignored) {}

        if (root == null) {
            JSONObject empty = new JSONObject();
            empty.put("flag_secure_or_empty", true);
            empty.put("nodes", new JSONArray());
            return empty;
        }

        JSONArray nodesArray = new JSONArray();
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);

        int count = 0;
        try {
            while (!queue.isEmpty() && count < MAX_NODES) {
                AccessibilityNodeInfo current = queue.poll();
                if (current == null) continue;

                if (current.isVisibleToUser()) {
                    JSONObject nodeObj = new JSONObject();
                    nodeObj.put("id", "native_" + count);
                    nodeObj.put("className", current.getClassName() != null ? current.getClassName().toString() : "");
                    nodeObj.put("packageName", current.getPackageName() != null ? current.getPackageName().toString() : "");
                    nodeObj.put("viewId", current.getViewIdResourceName() != null ? current.getViewIdResourceName() : "");
                    nodeObj.put("clickable", current.isClickable());
                    nodeObj.put("editable", current.isEditable());
                    nodeObj.put("scrollable", current.isScrollable());

                    Rect bounds = new Rect();
                    current.getBoundsInScreen(bounds);
                    JSONObject boundsObj = new JSONObject();
                    boundsObj.put("left", bounds.left);
                    boundsObj.put("top", bounds.top);
                    boundsObj.put("right", bounds.right);
                    boundsObj.put("bottom", bounds.bottom);
                    boundsObj.put("centerX", bounds.centerX());
                    boundsObj.put("centerY", bounds.centerY());
                    nodeObj.put("bounds", boundsObj);

                    boolean sensitive = isSensitiveNode(current);
                    nodeObj.put("sensitive", sensitive);

                    if (sensitive) {
                        nodeObj.put("text", "[REDACTED]");
                        nodeObj.put("contentDescription", "[REDACTED]");
                    } else {
                        nodeObj.put("text", current.getText() != null ? current.getText().toString() : "");
                        nodeObj.put("contentDescription", current.getContentDescription() != null ? current.getContentDescription().toString() : "");
                    }

                    nodesArray.put(nodeObj);
                    count++;
                }

                for (int i = 0; i < current.getChildCount(); i++) {
                    AccessibilityNodeInfo child = current.getChild(i);
                    if (child != null) {
                        queue.add(child);
                    }
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= 30) {
                try { root.recycle(); } catch (Exception ignored) {}
            }
        }

        JSONObject snapshot = new JSONObject();
        snapshot.put("nodes", nodesArray);
        snapshot.put("count", count);
        return snapshot;
    }

    public static ActionResult click(AccessibilityService service, String targetTextOrId) {
        if (service == null || targetTextOrId == null || targetTextOrId.isEmpty()) {
            return new ActionResult(false, "Invalid service or target");
        }
        AccessibilityNodeInfo root = null;
        try { root = service.getRootInActiveWindow(); } catch (Exception ignored) {}
        if (root == null) return new ActionResult(false, "Could not get root window");

        try {
            // Try by View ID
            List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId(targetTextOrId);
            if (byId != null && !byId.isEmpty()) {
                for (AccessibilityNodeInfo node : byId) {
                    if (performClick(service, node)) return new ActionResult(true, "Clicked node by ID " + targetTextOrId);
                }
            }

            // Try by text
            List<AccessibilityNodeInfo> byText = root.findAccessibilityNodeInfosByText(targetTextOrId);
            if (byText != null && !byText.isEmpty()) {
                for (AccessibilityNodeInfo node : byText) {
                    if (performClick(service, node)) return new ActionResult(true, "Clicked node with text " + targetTextOrId);
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= 30) {
                try { root.recycle(); } catch (Exception ignored) {}
            }
        }
        return new ActionResult(false, "Node not found for target " + targetTextOrId);
    }

    public static boolean performClick(AccessibilityService service, AccessibilityNodeInfo node) {
        if (node == null) return false;
        // Check node itself or walk up clickable parents
        AccessibilityNodeInfo curr = node;
        while (curr != null) {
            if (curr.isClickable() && curr.isEnabled()) {
                return curr.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            curr = curr.getParent();
        }

        // Coordinate tap fallback via dispatchGesture
        if (Build.VERSION.SDK_INT >= 24 && service != null) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (!bounds.isEmpty()) {
                Path path = new Path();
                path.moveTo(bounds.centerX(), bounds.centerY());
                GestureDescription.Builder builder = new GestureDescription.Builder();
                builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
                return service.dispatchGesture(builder.build(), null, null);
            }
        }
        return false;
    }

    public static ActionResult setText(AccessibilityService service, String targetIdOrText, String text) {
        if (service == null) return new ActionResult(false, "Service not available");
        AccessibilityNodeInfo root = null;
        try { root = service.getRootInActiveWindow(); } catch (Exception ignored) {}
        if (root == null) return new ActionResult(false, "Could not get root window");

        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(targetIdOrText);
            if (nodes == null || nodes.isEmpty()) {
                nodes = root.findAccessibilityNodeInfosByText(targetIdOrText);
            }
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isEditable() || (Build.VERSION.SDK_INT >= 26 && node.isFocused())) {
                        Bundle arguments = new Bundle();
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                        if (ok) return new ActionResult(true, "Text entered");
                    }
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= 30) {
                try { root.recycle(); } catch (Exception ignored) {}
            }
        }
        return new ActionResult(false, "Editable node not found");
    }

    public static ActionResult scroll(AccessibilityService service, boolean forward) {
        if (service == null) return new ActionResult(false, "Service not available");
        AccessibilityNodeInfo root = null;
        try { root = service.getRootInActiveWindow(); } catch (Exception ignored) {}
        if (root == null) return new ActionResult(false, "Could not get root window");

        try {
            int action = forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                AccessibilityNodeInfo current = queue.poll();
                if (current == null) continue;
                if (current.isScrollable()) {
                    boolean ok = current.performAction(action);
                    if (ok) return new ActionResult(true, "Scrolled " + (forward ? "forward" : "backward"));
                }
                for (int i = 0; i < current.getChildCount(); i++) {
                    AccessibilityNodeInfo child = current.getChild(i);
                    if (child != null) queue.add(child);
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= 30) {
                try { root.recycle(); } catch (Exception ignored) {}
            }
        }
        return new ActionResult(false, "No scrollable container found");
    }

    public static ActionResult back(AccessibilityService service) {
        if (service == null) return new ActionResult(false, "Service not available");
        boolean ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        return new ActionResult(ok, ok ? "Navigated back" : "Back action failed");
    }
}
