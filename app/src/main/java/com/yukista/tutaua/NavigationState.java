package com.yukista.tutaua;

import java.util.HashMap;
import java.util.Map;

final class NavigationState {
    private String section = "home";
    private String focusedMediaId;
    private boolean restoreFocus;
    private final Map<String, Integer> scrollPositions = new HashMap<>();

    String section() { return section; }
    void section(String value) { section = value; }
    void rememberMedia(String id) { focusedMediaId = id; }
    String focusedMediaId() { return focusedMediaId; }
    void requestRestore() { restoreFocus = focusedMediaId != null; }
    boolean consumeRestore() { boolean value = restoreFocus; restoreFocus = false; return value; }
    void resetFocus() { focusedMediaId = null; restoreFocus = false; }
    void rememberScroll(String section, int y) { scrollPositions.put(section, Math.max(0, y)); }
    int scrollPosition(String section) { Integer value = scrollPositions.get(section); return value == null ? 0 : value; }
}
