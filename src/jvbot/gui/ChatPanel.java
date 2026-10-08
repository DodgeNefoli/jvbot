package jvbot.gui;

import jvbot.Message;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

/**
 * The scrollable message area. User messages sit in rounded blue-tinted
 * cards, bot replies render as markdown under a green JVBOT label
 * (Claude-style), errors get an amber card. Content is centered with a max
 * width so text stays readable on a maximized window, and reflows on resize.
 */
public class ChatPanel extends JScrollPane {

    private static final int MAX_CONTENT_WIDTH = 840;
    private static final int MIN_SIDE_PAD = 24;

    private final Column column = new Column();

    // streaming state: raw text accumulates and is re-rendered ~12x/second
    private final StringBuilder streamBuffer = new StringBuilder();
    private final Timer streamTimer = new Timer(80, e -> flushStream());
    private boolean streamDirty;
    private MarkdownView streamingView;
    private JPanel streamingRow;

    private JPanel lastRow;
    private JPanel welcomePanel;       // empty-state hint, removed on first message

    public ChatPanel() {
        column.setBackground(Theme.BG);
        setViewportView(column);
        setBorder(null);
        getViewport().setBackground(Theme.BG);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        Theme.styleScrollBar(getVerticalScrollBar(), Theme.BG);
        getVerticalScrollBar().setUnitIncrement(18);

        // center the content: grow the side padding on wide windows
        getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updatePadding();
            }
        });
        updatePadding();
    }

    private void updatePadding() {
        int width = getViewport().getWidth();
        int side = Math.max(MIN_SIDE_PAD, (width - MAX_CONTENT_WIDTH) / 2);
        column.setBorder(BorderFactory.createEmptyBorder(20, side, 12, side));
        column.revalidate();
    }

    /**
     * Tracks the viewport width so the text panes always wrap instead of
     * growing sideways - the key to making text wrapping work here.
     */
    private static class Column extends JPanel implements Scrollable {
        Column() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle v, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle v, int o, int d) { return v.height; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ------------------------------------------------------------ public api

    public void clear() {
        streamTimer.stop();
        column.removeAll();
        streamingView = null;
        streamingRow = null;
        streamBuffer.setLength(0);
        streamDirty = false;
        welcomePanel = null;
        refresh();
    }

    /** Empty-state hint shown for a fresh chat. */
    public void showWelcome(boolean active) {
        clear();
        String text = active
                ? "<html><div style='text-align:center;'>Say hello!<br>Chats save automatically in the chats folder.</div></html>"
                : "<html><div style='text-align:center;'>Welcome to JVBot.<br><br>Open <b>Settings</b> and add your free API key<br>from build.nvidia.com to activate the bot.</div></html>";
        JLabel label = new JLabel(text);
        label.setFont(Theme.UI);
        label.setForeground(Theme.TEXT_DIM);
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(90, 0, 0, 0));
        wrap.add(label);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        column.add(wrap);
        welcomePanel = wrap;
        refresh();
    }

    /** Render a whole (loaded) conversation. */
    public void showMessages(List<Message> messages) {
        clear();
        for (Message m : messages) {
            if ("user".equals(m.role)) {
                addUserBubble(m.content);
            } else {
                addBotView(m.content);
            }
        }
        scrollToBottom();
    }

    public void addUserBubble(String text) {
        JTextArea area = plainArea(text, Theme.TEXT);
        area.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        Theme.Card card = new Theme.Card(Theme.USER_CARD);
        card.add(area, BorderLayout.CENTER);
        addRow("YOU", Theme.TEXT_DIM, card);
        scrollToBottom();
    }

    public void addErrorBubble(String text) {
        JTextArea area = plainArea(text, new Color(0xEED9AC));
        area.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        Theme.Card card = new Theme.Card(Theme.ERROR_CARD);
        card.add(area, BorderLayout.CENTER);
        addRow("ERROR", Theme.WARN, card);
        scrollToBottom();
    }

    /** Adds a bot block with a "..." placeholder that tokens will replace. */
    public void startBotBubble() {
        streamBuffer.setLength(0);
        streamDirty = false;
        streamingView = addBotView("...");
        streamingRow = lastRow;
        streamTimer.start();
        scrollToBottom();
    }

    /** Called from the EDT for every streamed token. */
    public void appendToBotBubble(String piece) {
        streamBuffer.append(piece);
        streamDirty = true;    // the timer renders it shortly
    }

    /** Ends streaming; removes the block if it never received any tokens. */
    public void finishBotBubble() {
        streamTimer.stop();
        if (streamBuffer.length() == 0 && streamingRow != null) {
            column.remove(streamingRow);
            refresh();
        } else if (streamingView != null) {
            streamingView.setMarkdown(streamBuffer.toString());   // exact final render
            refresh();
            scrollToBottom();
        }
        streamingView = null;
        streamingRow = null;
        streamBuffer.setLength(0);
        streamDirty = false;
    }

    // -------------------------------------------------------------- internal

    private void flushStream() {
        if (streamingView == null || !streamDirty) return;
        streamDirty = false;
        streamingView.setMarkdown(streamBuffer.toString());
        refresh();
        scrollToBottom();
    }

    private JTextArea plainArea(String text, Color color) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(Theme.CHAT);
        area.setForeground(color);
        area.setCaretColor(color);
        return area;
    }

    /** Bot messages: markdown, no card - plain on the background. */
    private MarkdownView addBotView(String markdown) {
        MarkdownView view = new MarkdownView();
        view.setMarkdown(markdown);
        view.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(view, BorderLayout.CENTER);
        addRow("JVBOT", Theme.ACCENT, holder);
        return view;
    }

    private void addRow(String roleLabel, Color roleColor, JComponent content) {
        if (welcomePanel != null) {
            column.remove(welcomePanel);   // first message replaces the hint
            welcomePanel = null;
        }
        JLabel label = new JLabel(roleLabel);
        label.setFont(Theme.SMALL_BOLD);
        label.setForeground(roleColor);
        label.setBorder(BorderFactory.createEmptyBorder(0, 6, 5, 0));

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        row.add(label, BorderLayout.NORTH);
        row.add(content, BorderLayout.CENTER);
        row.setAlignmentX(LEFT_ALIGNMENT);

        column.add(row);
        lastRow = row;
        refresh();
    }

    private void refresh() {
        column.revalidate();
        column.repaint();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() ->
                getVerticalScrollBar().setValue(getVerticalScrollBar().getMaximum()));
    }
}
