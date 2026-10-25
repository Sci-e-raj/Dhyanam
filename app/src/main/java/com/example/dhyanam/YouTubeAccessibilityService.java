package com.example.dhyanam;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class YouTubeAccessibilityService extends AccessibilityService {
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    // Observed on YouTube 21.42.243 in the tab, Home, and link Shorts paths.
    private static final String OBSERVED_REEL_TIME_BAR_ID =
            "com.google.android.youtube:id/reel_time_bar";
    private static final String LOG_TAG = "FocusBuddyA11y";
    private static final long SNAPSHOT_INTERVAL_MS = 1500;
    private static final long CONFIRM_DELAY_MS = 400;
    private static final long RESET_DELAY_MS = 1000;
    private static final long BACK_COOLDOWN_MS = 5000;
    private static final int MAX_NODES = 24;
    private static final int MAX_DEPTH = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable confirmBack = this::confirmAndGoBack;
    private final Runnable resetEncounter = this::resetAfterLeavingPlayer;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            (preferences, key) -> handler.post(() -> {
                if (!BlockingPreferences.isBlockingEnabled(this)) {
                    cancelPendingBack();
                    handler.removeCallbacks(resetEncounter);
                    pendingReset = false;
                    backAttemptedForEncounter = false;
                }
            });
    private SharedPreferences preferences;
    private boolean pendingBack;
    private boolean pendingReset;
    private boolean backAttemptedForEncounter;
    private long lastBackAt = -BACK_COOLDOWN_MS;
    private long lastSnapshotTime;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        preferences = BlockingPreferences.preferences(this);
        preferences.registerOnSharedPreferenceChangeListener(settingsListener);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !YOUTUBE_PACKAGE.equals(String.valueOf(event.getPackageName()))) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED
                && type != AccessibilityEvent.TYPE_VIEW_CLICKED) return;

        boolean blockingEnabled = BlockingPreferences.isBlockingEnabled(this);
        boolean debug = isDebugBuild();
        if (!blockingEnabled) {
            cancelPendingBack();
            handler.removeCallbacks(resetEncounter);
            pendingReset = false;
            backAttemptedForEncounter = false;
            if (!debug) return;
        }

        AccessibilityNodeInfo root = currentRoot();
        if (root == null) {
            if (debug && shouldLog(type)) logUnavailable(type, event);
            return;
        }
        try {
            if (!YOUTUBE_PACKAGE.equals(String.valueOf(root.getPackageName()))) {
                cancelPendingBack();
                scheduleResetIfNeeded();
                return;
            }

            Boolean reelUiCandidate = hasObservedReelTimeBar(root);
            if (Boolean.TRUE.equals(reelUiCandidate)) {
                if (blockingEnabled) {
                    // YouTube can keep the reel view attached after Back. A later
                    // user tap is a new chance to intervene, even if no "absent"
                    // snapshot was delivered between the two visits.
                    if (backAttemptedForEncounter
                            && type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                        backAttemptedForEncounter = false;
                        if (debug) Log.d(LOG_TAG, "intervention=rearmed reason=youtube_tap");
                    }
                    scheduleBackConfirmation();
                }
            } else if (Boolean.FALSE.equals(reelUiCandidate)) {
                cancelPendingBack();
                scheduleResetIfNeeded();
            }

            if (debug && shouldLog(type)) logSnapshot(type, event, root, reelUiCandidate);
        } finally {
            root.recycle();
        }
    }

    private void scheduleBackConfirmation() {
        if (pendingBack || backAttemptedForEncounter) return;
        long sinceLastBack = SystemClock.elapsedRealtime() - lastBackAt;
        long delay = Math.max(CONFIRM_DELAY_MS, BACK_COOLDOWN_MS - sinceLastBack);
        pendingBack = true;
        if (!handler.postDelayed(confirmBack, delay)) pendingBack = false;
    }

    private void confirmAndGoBack() {
        pendingBack = false;
        if (!BlockingPreferences.isBlockingEnabled(this) || backAttemptedForEncounter
                || SystemClock.elapsedRealtime() - lastBackAt < BACK_COOLDOWN_MS) return;

        AccessibilityNodeInfo root = currentRoot();
        if (root == null) return;
        try {
            if (!YOUTUBE_PACKAGE.equals(String.valueOf(root.getPackageName()))
                    || !Boolean.TRUE.equals(hasObservedReelTimeBar(root))) return;
        } finally {
            root.recycle();
        }

        // Latch before asking Android to go Back, so failure cannot cause an action loop.
        backAttemptedForEncounter = true;
        lastBackAt = SystemClock.elapsedRealtime();
        boolean performed;
        try {
            performed = performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (RuntimeException exception) {
            performed = false;
        }
        if (isDebugBuild()) Log.d(LOG_TAG, "intervention=back performed=" + performed);
        scheduleResetIfNeeded();
    }

    private void scheduleResetIfNeeded() {
        if (backAttemptedForEncounter && !pendingReset) {
            pendingReset = true;
            if (!handler.postDelayed(resetEncounter, RESET_DELAY_MS)) pendingReset = false;
        }
    }

    private void resetAfterLeavingPlayer() {
        pendingReset = false;
        if (!backAttemptedForEncounter) return;
        AccessibilityNodeInfo root = currentRoot();
        if (root != null) {
            try {
                if (!YOUTUBE_PACKAGE.equals(String.valueOf(root.getPackageName()))
                        || Boolean.FALSE.equals(hasObservedReelTimeBar(root))) {
                    backAttemptedForEncounter = false;
                    if (isDebugBuild()) Log.d(LOG_TAG, "intervention=rearmed reason=left_player");
                    return;
                }
            } finally {
                root.recycle();
            }
        }
        // Keep checking until a different screen is actually observed. A timeout alone
        // would leave the latch stuck if YouTube sends no event while exiting Shorts.
        scheduleResetIfNeeded();
    }

    private void cancelPendingBack() {
        handler.removeCallbacks(confirmBack);
        pendingBack = false;
    }

    private AccessibilityNodeInfo currentRoot() {
        try {
            return getRootInActiveWindow();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Boolean hasObservedReelTimeBar(AccessibilityNodeInfo root) {
        try {
            List<AccessibilityNodeInfo> matches =
                    root.findAccessibilityNodeInfosByViewId(OBSERVED_REEL_TIME_BAR_ID);
            if (matches == null) return null;
            for (AccessibilityNodeInfo match : matches) match.recycle();
            return !matches.isEmpty();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean shouldLog(int type) {
        long now = SystemClock.elapsedRealtime();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && now - lastSnapshotTime < SNAPSHOT_INTERVAL_MS) return false;
        lastSnapshotTime = now;
        return true;
    }

    private void logUnavailable(int type, AccessibilityEvent event) {
        Log.d(LOG_TAG, "event=" + AccessibilityEvent.eventTypeToString(type)
                + " windowClass=" + safe(event.getClassName())
                + " reelUiCandidate=unknown root=unavailable");
    }

    private void logSnapshot(int type, AccessibilityEvent event,
                             AccessibilityNodeInfo root, Boolean candidate) {
        StringBuilder structure = new StringBuilder();
        int[] count = {0};
        try {
            appendStructure(root, 0, count, structure);
        } catch (RuntimeException exception) {
            structure.setLength(0);
            structure.append("unavailable");
        }
        Log.d(LOG_TAG, "event=" + AccessibilityEvent.eventTypeToString(type)
                + " windowClass=" + safe(event.getClassName())
                + " source=" + describeSource(event)
                + " reelUiCandidate=" + (candidate == null ? "unknown" : candidate)
                + " backAttemptedForEncounter=" + backAttemptedForEncounter
                + " pendingBack=" + pendingBack
                + " nodes=" + count[0] + " structure=" + structure);
    }

    private String describeSource(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return "-";
        try {
            if (!YOUTUBE_PACKAGE.equals(String.valueOf(source.getPackageName()))) return "-";
            return safe(source.getClassName()) + "/" + safe(source.getViewIdResourceName());
        } finally {
            source.recycle();
        }
    }

    private void appendStructure(AccessibilityNodeInfo node, int depth,
                                 int[] count, StringBuilder structure) {
        if (count[0] >= MAX_NODES || depth > MAX_DEPTH) return;
        count[0]++;
        if (structure.length() > 0) structure.append(" | ");
        structure.append(depth).append(':')
                .append(safe(node.getClassName()))
                .append(" id=").append(safe(node.getViewIdResourceName()))
                .append(" children=").append(node.getChildCount())
                .append(" clickable=").append(node.isClickable())
                .append(" scrollable=").append(node.isScrollable());
        for (int i = 0; i < node.getChildCount() && count[0] < MAX_NODES; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                appendStructure(child, depth + 1, count, structure);
                child.recycle();
            }
        }
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static String safe(CharSequence value) {
        return value == null ? "-" : value.toString();
    }

    @Override
    public void onInterrupt() {
        cancelPendingBack();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(settingsListener);
        super.onDestroy();
    }
}
