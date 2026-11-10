package scapms.gui;

/** Implemented by every panel that must redraw itself when the clinic data changes. */
interface Refreshable {
    void refresh();
}
