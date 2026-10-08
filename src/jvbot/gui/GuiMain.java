package jvbot.gui;

import jvbot.ChatSession;
import jvbot.HistoryStore;
import jvbot.NvidiaClient;
import jvbot.Settings;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * JVBot desktop app (Swing). Same brain as the console version - shared
 * Settings, NvidiaClient, ChatSession and HistoryStore - with a modern dark
 * Claude-style UI: sidebar with new/continue/delete chat, streaming replies,
 * and a Settings dialog that activates the bot when a key is saved.
 */
public class GuiMain {

    private final Settings settings;
    private final HistoryStore store;
    private final NvidiaClient client;
    private ChatSession session;

    private JFrame frame;
    private ChatPanel chatPanel;
    private JTextArea input;
    private JScrollPane inputScroll;
    private Theme.InputCard inputCard;
    private Theme.RoundButton sendButton;
    private Theme.Card statusPill;
    private JLabel statusLabel;
    private javax.swing.JComboBox<String> modelCombo;
    private JPanel chatListPanel;

    private boolean busy;            // a request is in flight
    private boolean updatingModels;  // guard while the combo list is rebuilt

    /** One sidebar row worth of data - cached so painting never reads files. */
    private static class ChatEntry {
        final Path file;
        final String title;
        final String sub;

        ChatEntry(Path file, String title, String sub) {
            this.file = file;
            this.title = title;
            this.sub = sub;
        }
    }

    public static void main(String[] args) {
        // crisp text everywhere - must be set before Swing initializes
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        String autotestMessage = args.length >= 2 && args[0].equals("--autotest") ? args[1] : null;
        String autotestSize = args.length >= 3 ? args[2] : null;
        SwingUtilities.invokeLater(() -> new GuiMain().start(autotestMessage, autotestSize));
    }

    private GuiMain() {
        Path base = Paths.get("").toAbsolutePath();
        settings = new Settings(base.resolve("settings.properties"));
        store = new HistoryStore(base.resolve("chats"));
        client = new NvidiaClient(settings);
        session = new ChatSession(store);
    }

    private void start(String autotestMessage, String autotestSize) {
        frame = new JFrame("JVBot");
        frame.setIconImage(appIcon());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                session.save(settings.getModel());
            }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.add(buildSidebar(), BorderLayout.WEST);
        content.add(buildMainArea(), BorderLayout.CENTER);
        frame.setContentPane(content);

        frame.setSize(1020, 680);
        frame.setMinimumSize(new Dimension(720, 480));
        frame.setLocationRelativeTo(null);

        refreshSidebar();
        refreshStatus();
        rebuildModelCombo();
        chatPanel.showWelcome(settings.hasKey());

        if (autotestMessage != null) {
            // used by automated verification and demos: pin the window and auto-send
            frame.setLocation(10, 10);
            frame.setAlwaysOnTop(true);
            if (autotestSize != null && autotestSize.matches("\\d+x\\d+")) {
                String[] wh = autotestSize.split("x");
                frame.setSize(Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
            }
            Timer t = new Timer(1500, e -> {
                input.setText(autotestMessage);
                send();
            });
            t.setRepeats(false);
            t.start();
        }

        frame.setVisible(true);
        input.requestFocusInWindow();
    }

    // -------------------------------------------------------------- sidebar

    private JPanel buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.SIDEBAR);
        side.setPreferredSize(new Dimension(250, 0));
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        JButton newChat = new Theme.RoundButton("+  New Chat", Theme.SELECTED, Theme.TEXT);
        newChat.addActionListener(e -> startNewChat());
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(14, 12, 10, 12));
        top.add(newChat, BorderLayout.CENTER);
        side.add(top, BorderLayout.NORTH);

        chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        chatListPanel.setBackground(Theme.SIDEBAR);
        chatListPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JPanel listHolder = new JPanel(new BorderLayout());
        listHolder.setBackground(Theme.SIDEBAR);
        listHolder.add(chatListPanel, BorderLayout.NORTH);   // rows keep natural height

        JScrollPane scroll = new JScrollPane(listHolder);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Theme.SIDEBAR);
        Theme.styleScrollBar(scroll.getVerticalScrollBar(), Theme.SIDEBAR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        side.add(scroll, BorderLayout.CENTER);

        JButton settingsButton = new Theme.RoundButton("Settings", Theme.SIDEBAR, Theme.TEXT_DIM);
        settingsButton.addActionListener(e -> openSettings());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 12, 14, 12));
        bottom.add(settingsButton, BorderLayout.CENTER);
        side.add(bottom, BorderLayout.SOUTH);

        return side;
    }

    /** Rebuilds the sidebar rows from the chats folder. */
    private void refreshSidebar() {
        chatListPanel.removeAll();
        String currentFile = session.fileName();
        for (Path f : store.list()) {
            String sub = dateOf(f) + "  ·  " + store.messageCount(f) + " messages";
            ChatEntry entry = new ChatEntry(f, store.title(f), sub);
            boolean active = f.getFileName().toString().equals(currentFile);
            chatListPanel.add(chatRow(entry, active));
            chatListPanel.add(Box.createVerticalStrut(2));
        }
        chatListPanel.revalidate();
        chatListPanel.repaint();
    }

    /** One Claude-style sidebar row: hover highlight + delete on hover. */
    private JPanel chatRow(ChatEntry entry, boolean active) {
        Theme.Card row = new Theme.Card(active ? Theme.SELECTED : null, 12);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 6));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));

        JLabel title = new JLabel(entry.title);
        title.setFont(Theme.UI);
        title.setForeground(active ? Theme.TEXT : Theme.TEXT_DIM);

        JLabel sub = new JLabel(entry.sub);
        sub.setFont(Theme.SMALL);
        sub.setForeground(Theme.TEXT_DIM);

        JPanel meta = new JPanel();
        meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));
        meta.setOpaque(false);
        title.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        sub.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        meta.add(title);
        meta.add(Box.createVerticalStrut(2));
        meta.add(sub);

        JButton delete = new JButton("✕");
        delete.setFont(Theme.SMALL);
        delete.setForeground(Theme.TEXT_DIM);
        delete.setContentAreaFilled(false);
        delete.setBorderPainted(false);
        delete.setFocusPainted(false);
        delete.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        delete.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        delete.setVisible(false);
        delete.setToolTipText("Delete chat");
        delete.addActionListener(e -> deleteChat(entry));
        delete.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { delete.setForeground(Theme.DANGER); }
            @Override
            public void mouseExited(MouseEvent e) { delete.setForeground(Theme.TEXT_DIM); }
        });

        row.add(meta, BorderLayout.CENTER);
        row.add(delete, BorderLayout.EAST);

        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!active) row.setFill(Theme.HOVER);
                delete.setVisible(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // ignore "exits" onto our own children (e.g. the delete button)
                if (row.contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row))) return;
                if (!active) row.setFill(null);
                delete.setVisible(false);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getComponent() != delete) openChat(entry.file);
            }
        };
        row.addMouseListener(hover);
        meta.addMouseListener(hover);
        title.addMouseListener(hover);
        sub.addMouseListener(hover);
        delete.addMouseListener(hover);

        return row;
    }

    /** "chat-2026-07-14_142736.json" -> "2026-07-14 14:27" */
    private static String dateOf(Path file) {
        String name = file.getFileName().toString();
        if (name.length() >= 22) {
            return name.substring(5, 15) + " " + name.substring(16, 18) + ":" + name.substring(18, 20);
        }
        return "";
    }

    // ------------------------------------------------------------ main area

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);

        // header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(11, 20, 11, 20)));

        JLabel appName = new JLabel("JVBot");
        appName.setFont(Theme.TITLE);
        appName.setForeground(Theme.TEXT);

        // switcher for YOUR saved models (the model+key pairs from Settings)
        modelCombo = new javax.swing.JComboBox<>();
        Theme.styleCombo(modelCombo);
        modelCombo.setFont(Theme.SMALL);
        modelCombo.setMaximumRowCount(18);
        modelCombo.setPreferredSize(new Dimension(280, 30));
        modelCombo.setToolTipText("Your saved models - add more in Settings");
        modelCombo.addActionListener(e -> {
            if (updatingModels) return;
            int index = modelCombo.getSelectedIndex();
            if (index >= 0 && index != settings.getActiveProfile()) {
                settings.setActiveProfile(index);   // switches model AND its key
                refreshStatus();
            }
        });

        JPanel comboHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        comboHolder.setOpaque(false);
        comboHolder.add(modelCombo);

        // BorderLayout so the app name always keeps its full width
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(appName, BorderLayout.WEST);
        left.add(comboHolder, BorderLayout.CENTER);

        statusLabel = new JLabel();
        statusLabel.setFont(Theme.SMALL_BOLD);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        statusPill = new Theme.Card(Theme.WARN_BG, 999);
        statusPill.add(statusLabel, BorderLayout.CENTER);
        statusPill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusPill.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!settings.hasKey()) openSettings();
            }
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(statusPill);

        header.add(left, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);

        // chat area
        chatPanel = new ChatPanel();
        main.add(chatPanel, BorderLayout.CENTER);

        // input bar: auto-growing text area, Enter sends, Shift+Enter = newline
        input = new JTextArea(1, 10);
        input.setFont(Theme.CHAT);
        input.setBackground(Theme.INPUT_BG);
        input.setForeground(Theme.TEXT);
        input.setCaretColor(Theme.TEXT);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setBorder(BorderFactory.createEmptyBorder(11, 14, 11, 14));

        InputMap im = input.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = input.getActionMap();
        im.put(KeyStroke.getKeyStroke("ENTER"), "jvbot-send");
        im.put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        am.put("jvbot-send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });

        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { growInput(); }
            public void removeUpdate(DocumentEvent e) { growInput(); }
            public void changedUpdate(DocumentEvent e) { growInput(); }
        });
        input.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) { inputCard.setFocused(true); }
            @Override
            public void focusLost(FocusEvent e) { inputCard.setFocused(false); }
        });

        inputScroll = new JScrollPane(input);
        inputScroll.setBorder(null);
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(true);
        inputScroll.getViewport().setBackground(Theme.INPUT_BG);
        inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Theme.styleScrollBar(inputScroll.getVerticalScrollBar(), Theme.INPUT_BG);

        inputCard = new Theme.InputCard();
        inputCard.add(inputScroll, BorderLayout.CENTER);

        sendButton = new Theme.RoundButton("Send", Theme.PRIMARY, Theme.TEXT);

        sendButton.addActionListener(e -> send());

        JPanel sendHolder = new JPanel(new BorderLayout());
        sendHolder.setOpaque(false);
        sendHolder.add(sendButton, BorderLayout.SOUTH);   // stays aligned as input grows

        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(Theme.BG);
        bar.setBorder(BorderFactory.createEmptyBorder(8, 20, 16, 20));
        bar.add(inputCard, BorderLayout.CENTER);
        bar.add(sendHolder, BorderLayout.EAST);
        main.add(bar, BorderLayout.SOUTH);

        growInput();
        return main;
    }

    /** Sizes the input between 1 and 4 text rows, scrolling beyond that. */
    private void growInput() {
        int lineHeight = input.getFontMetrics(input.getFont()).getHeight();
        int padding = 22;   // input's top+bottom border
        int wanted = input.getPreferredSize().height;
        int min = lineHeight + padding;
        int max = lineHeight * 4 + padding;
        int height = Math.max(min, Math.min(wanted, max)) + 2;   // +2 for the card border
        Dimension d = new Dimension(0, height);
        inputScroll.setPreferredSize(d);
        inputScroll.revalidate();
        inputCard.revalidate();
        inputCard.getParent().revalidate();
    }

    private void refreshStatus() {
        if (settings.hasKey()) {
            statusLabel.setText("● active");
            statusLabel.setForeground(Theme.ACCENT);
            statusPill.setFill(Theme.ACCENT_BG);
            statusPill.setToolTipText(null);
        } else {
            statusLabel.setText("● add key in Settings");
            statusLabel.setForeground(Theme.WARN);
            statusPill.setFill(Theme.WARN_BG);
            statusPill.setToolTipText("Click to open Settings");
        }
    }

    /** Rebuilds the header dropdown from the saved model+key profiles. */
    private void rebuildModelCombo() {
        updatingModels = true;
        try {
            modelCombo.removeAllItems();
            List<jvbot.Settings.Profile> profiles = settings.getProfiles();
            for (int i = 0; i < profiles.size(); i++) {
                modelCombo.addItem(profileLabel(profiles, i));
            }
            int active = settings.getActiveProfile();
            if (active >= 0) {
                modelCombo.setSelectedIndex(active);
            } else if (modelCombo.getItemCount() == 0) {
                modelCombo.addItem(settings.getModel());   // nothing saved yet
                modelCombo.setSelectedIndex(0);
            }
        } finally {
            updatingModels = false;
        }
    }

    /** Model name; if the same model is saved with two keys, show which key. */
    private static String profileLabel(List<jvbot.Settings.Profile> profiles, int i) {
        String model = profiles.get(i).model;
        for (int j = 0; j < profiles.size(); j++) {
            if (j != i && profiles.get(j).model.equals(model)) {
                String key = profiles.get(i).key;
                String tail = key.length() > 4 ? key.substring(key.length() - 4) : key;
                return model + "  (key …" + tail + ")";
            }
        }
        return model;
    }

    // -------------------------------------------------------------- actions

    private void openSettings() {
        boolean wasInactive = !settings.hasKey();
        new SettingsDialog(frame, settings, client, () -> {
            refreshStatus();
            rebuildModelCombo();   // saved models may have changed
        }).setVisible(true);
        refreshStatus();
        rebuildModelCombo();
        if (wasInactive && settings.hasKey() && session.isEmpty()) {
            chatPanel.showWelcome(true);   // bot just activated - update the hint
        }
    }

    private void startNewChat() {
        if (busy) return;
        session.save(settings.getModel());
        session = new ChatSession(store);
        chatPanel.showWelcome(settings.hasKey());
        refreshSidebar();
        input.requestFocusInWindow();
    }

    private void openChat(Path file) {
        if (busy) return;
        session.save(settings.getModel());
        session = ChatSession.resume(store, file);
        chatPanel.showMessages(session.messages());
        refreshSidebar();
        input.requestFocusInWindow();
    }

    private void deleteChat(ChatEntry entry) {
        if (busy) return;
        int choice = JOptionPane.showConfirmDialog(frame,
                "Delete \"" + entry.title + "\"?",
                "Delete chat", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;
        store.delete(entry.file);
        if (entry.file.getFileName().toString().equals(session.fileName())) {
            session = new ChatSession(store);   // the open chat was deleted
            chatPanel.showWelcome(settings.hasKey());
        }
        refreshSidebar();
    }

    private void send() {
        String text = input.getText().trim();
        if (text.isEmpty() || busy) return;
        if (!settings.hasKey()) {
            openSettings();               // activation lives in Settings
            if (!settings.hasKey()) return;
        }

        input.setText("");
        setBusy(true);
        session.add("user", text);
        chatPanel.addUserBubble(text);
        chatPanel.startBotBubble();

        Thread worker = new Thread(() -> {
            try {
                String reply = client.chat(session.messages(), piece ->
                        SwingUtilities.invokeLater(() -> chatPanel.appendToBotBubble(piece)));
                SwingUtilities.invokeLater(() -> {
                    chatPanel.finishBotBubble();
                    session.add("assistant", reply);
                    session.save(settings.getModel());
                    refreshSidebar();
                    setBusy(false);
                });
            } catch (NvidiaClient.ApiException e) {
                SwingUtilities.invokeLater(() -> {
                    chatPanel.finishBotBubble();
                    chatPanel.addErrorBubble(e.getMessage());
                    session.removeLast();     // the question got no answer
                    setBusy(false);
                });
            }
        }, "jvbot-request");
        worker.start();
    }

    private void setBusy(boolean value) {
        busy = value;
        sendButton.setEnabled(!value);
        sendButton.setText(value ? "..." : "Send");
        if (!value) input.requestFocusInWindow();
    }

    /** Taskbar/window icon drawn in code - a rounded square with a J. */
    private static java.awt.image.BufferedImage appIcon() {
        int size = 64;
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.PRIMARY);
        g.fillRoundRect(2, 2, size - 4, size - 4, 22, 22);
        g.setColor(Theme.TEXT);
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 40));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString("J", (size - fm.stringWidth("J")) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return img;
    }
}
