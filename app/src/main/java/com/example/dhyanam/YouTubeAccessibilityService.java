package com.example.dhyanam;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class YouTubeAccessibilityService extends AccessibilityService {
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    // Observed in all three Shorts paths on YouTube 21.42.243; absent in the regular-video sample.
    private static final String OBSERVED_REEL_TIME_BAR_ID =
            "com.google.android.youtube:id/reel_time_bar";
    private static final String LOG_TAG = "FocusBuddyA11y";
    private static final long SNAPSHOT_INTERVAL_MS = 1500;
    private static final int MAX_NODES = 24;
    private static final int MAX_DEPTH = 3;
    private long lastSnapshotTime;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0
                || event == null || !YOUTUBE_PACKAGE.equals(String.valueOf(event.getPackageName()))) {
            return;
        }

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED
                && type != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && now - lastSnapshotTime < SNAPSHOT_INTERVAL_MS) {
            return;
        }
        lastSnapshotTime = now;
        String source = describeSource(event);

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.d(LOG_TAG, "event=" + AccessibilityEvent.eventTypeToString(type)
                    + " windowClass=" + safe(event.getClassName())
                    + " source=" + source + " reelUiCandidate=unknown root=unavailable");
            return;
        }
        if (!YOUTUBE_PACKAGE.equals(String.valueOf(root.getPackageName()))) {
            root.recycle();
            return;
        }

        StringBuilder structure = new StringBuilder();
        int[] count = {0};
        appendStructure(root, 0, count, structure);
        boolean reelUiCandidate = hasObservedReelTimeBar(root);
        Log.d(LOG_TAG, "event=" + AccessibilityEvent.eventTypeToString(type)
                + " windowClass=" + safe(event.getClassName())
                + " source=" + source
                + " reelUiCandidate=" + reelUiCandidate
                + " nodes=" + count[0] + " structure=" + structure);
        root.recycle();
    }

    private boolean hasObservedReelTimeBar(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> matches =
                root.findAccessibilityNodeInfosByViewId(OBSERVED_REEL_TIME_BAR_ID);
        if (matches == null) return false;
        for (AccessibilityNodeInfo match : matches) match.recycle();
        return !matches.isEmpty();
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

    private static String safe(CharSequence value) {
        return value == null ? "-" : value.toString();
    }

    @Override
    public void onInterrupt() {
        // No user action is being performed in this milestone.
    }
}
