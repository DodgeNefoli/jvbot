package jvbot.gui;

import jvbot.NvidiaClient;
import jvbot.Settings;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.List;

/**
 * Settings window: manage the saved model+key pairs. Every pair you add
 * here becomes an option in the header dropdown - the bot only ever shows
 * models YOU added a key for. Adding the first pair ACTIVATES the bot.
 * All changes apply immediately.
 */
public class SettingsDialog extends JDialog {

    private final Settings settings;
    private final NvidiaClient client;
    private final Runnable onSaved;

    private final JComboBox<String> savedCombo = new JComboBox<>();
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JPasswordField keyField = new JPasswordField(22);
    private final JLabel modelStatus = new JLabel(" ");

    private boolean updatingSaved;   // guard while rebuilding the saved list

    public SettingsDialog(JFrame owner, Settings settings, NvidiaClient client, Runnable onSaved) {
        super(owner, "Settings", true);
        this.settings = settings;
        this.client = client;
        this.onSaved = onSaved;

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        gc.insets = new Insets(4, 0, 4, 0);

        // ---------------- saved models ----------------
        root.add(label("Your saved models  (each one remembers its own key)"), gc);

        Theme.styleCombo(savedCombo);
        savedCombo.addActionListener(e -> {
            if (updatingSaved) return;
            int index = savedCombo.getSelectedIndex();
            if (index >= 0 && index != settings.getActiveProfile()) {
                settings.setActiveProfile(index);
                onSaved.run();
            }
        });

        JButton remove = new Theme.RoundButton("Remove", Theme.INPUT_BG, Theme.DANGER);
        remove.addActionListener(e -> removeSelected());

        JPanel savedRow = new JPanel(new BorderLayout(8, 0));
        savedRow.setOpaque(false);
        savedRow.add(savedCombo, BorderLayout.CENTER);
        savedRow.add(remove, BorderLayout.EAST);
        gc.gridy++;
        gc.insets = new Insets(4, 0, 16, 0);
        root.add(savedRow, gc);

        // ---------------- add a new pair ----------------
        gc.insets = new Insets(4, 0, 4, 0);
        gc.gridy++;
        root.add(label("Add a model"), gc);

        modelCombo.setEditable(true);
        Theme.styleCombo(modelCombo);
        modelCombo.setMaximumRowCount(16);

        JButton refresh = new Theme.RoundButton("↻", Theme.INPUT_BG, Theme.TEXT_DIM);
        refresh.setToolTipText("Reload the model catalog");
        refresh.addActionListener(e -> fetchCatalog());

        JPanel modelRow = new JPanel(new BorderLayout(8, 0));
        modelRow.setOpaque(false);
        modelRow.add(modelCombo, BorderLayout.CENTER);
        modelRow.add(refresh, BorderLayout.EAST);
        gc.gridy++;
        root.add(modelRow, gc);

        modelStatus.setFont(Theme.SMALL);
        modelStatus.setForeground(Theme.TEXT_DIM);
        gc.gridy++;
        gc.insets = new Insets(2, 0, 8, 0);
        root.add(modelStatus, gc);

        gc.insets = new Insets(4, 0, 4, 0);
        gc.gridy++;
        root.add(label("...with its API key"), gc);

        styleField(keyField);
        JButton add = new Theme.RoundButton("Add", Theme.PRIMARY, Theme.TEXT);
        add.addActionListener(e -> addPair());

        JPanel keyRow = new JPanel(new BorderLayout(8, 0));
        keyRow.setOpaque(false);
        keyRow.add(keyField, BorderLayout.CENTER);
        keyRow.add(add, BorderLayout.EAST);
        gc.gridy++;
        root.add(keyRow, gc);

        JLabel hint = new JLabel("<html>Leave the key empty to reuse your active key for another model.<br>Get FREE keys at <u>build.nvidia.com</u> (they start with nvapi-).</html>");
        hint.setFont(Theme.SMALL);
        hint.setForeground(Theme.TEXT_DIM);
        hint.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hint.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://build.nvidia.com"));
                } catch (Exception ignored) {
                    // no browser available - the URL is visible in the label anyway
                }
            }
        });
        gc.gridy++;
        gc.insets = new Insets(2, 0, 14, 0);
        root.add(hint, gc);

        // ---------------- close ----------------
        JButton close = new Theme.RoundButton("Close", Theme.PRIMARY, Theme.TEXT);
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(close);
        gc.gridy++;
        gc.insets = new Insets(6, 0, 0, 0);
        root.add(buttons, gc);

        setContentPane(root);
        getRootPane().setDefaultButton(add);

        reloadSaved();
        modelCombo.getEditor().setItem(settings.getModel());
        if (settings.hasKey()) {
            fetchCatalog();
        } else {
            modelStatus.setText("Add your first key below - the catalog loads once you have one.");
        }

        setSize(500, getPreferredSize().height + 40);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // -------------------------------------------------------------- actions

    private void addPair() {
        String key = new String(keyField.getPassword()).trim();
        if (key.isEmpty()) {
            key = settings.getKey();   // reuse the active key for another model
            if (key.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Paste an API key first (get one free at build.nvidia.com).",
                        "No key", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } else if (!key.startsWith("nvapi-")) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "NVIDIA keys usually start with nvapi- . Add it anyway?",
                    "Unusual key", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        Object typed = modelCombo.getEditor().getItem();
        String model = typed == null ? "" : typed.toString().trim();

        boolean wasInactive = !settings.hasKey();
        settings.addProfile(model, key);   // saved + becomes active immediately
        keyField.setText("");
        reloadSaved();
        onSaved.run();
        if (wasInactive) {
            setTitle("Settings - bot activated!");
            fetchCatalog();
        }
    }

    private void removeSelected() {
        int index = savedCombo.getSelectedIndex();
        if (index < 0) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Remove \"" + savedCombo.getSelectedItem() + "\"?",
                "Remove model", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        settings.removeProfile(index);
        reloadSaved();
        onSaved.run();
    }

    /** Rebuilds the saved-models dropdown; selects the active profile. */
    private void reloadSaved() {
        updatingSaved = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            List<Settings.Profile> profiles = settings.getProfiles();
            for (int i = 0; i < profiles.size(); i++) {
                model.addElement(label(profiles, i));
            }
            savedCombo.setModel(model);
            int active = settings.getActiveProfile();
            if (active >= 0) savedCombo.setSelectedIndex(active);
        } finally {
            updatingSaved = false;
        }
    }

    /** Model name + masked key, so pairs are easy to tell apart. */
    private static String label(List<Settings.Profile> profiles, int i) {
        String key = profiles.get(i).key;
        String tail = key.length() > 4 ? key.substring(key.length() - 4) : key;
        return profiles.get(i).model + "   ·   key …" + tail;
    }

    /** Loads the model catalog (a helper for picking) on a background thread. */
    private void fetchCatalog() {
        if (!settings.hasKey()) return;
        modelStatus.setText("Loading the model catalog…");
        modelStatus.setForeground(Theme.TEXT_DIM);
        Thread worker = new Thread(() -> {
            try {
                List<String> models = client.listModels();
                SwingUtilities.invokeLater(() -> {
                    Object typed = modelCombo.getEditor().getItem();
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                    for (String m : models) model.addElement(m);
                    modelCombo.setModel(model);
                    modelCombo.getEditor().setItem(typed);   // keep what the user had
                    modelStatus.setText(models.size() + " models in the catalog - pick one or type your own");
                    modelStatus.setForeground(Theme.TEXT_DIM);
                });
            } catch (NvidiaClient.ApiException e) {
                SwingUtilities.invokeLater(() -> {
                    modelStatus.setText("Could not load the catalog: " + e.getMessage());
                    modelStatus.setForeground(Theme.WARN);
                });
            }
        }, "jvbot-catalog");
        worker.start();
    }

    // -------------------------------------------------------------- helpers

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.UI_BOLD);
        l.setForeground(Theme.TEXT);
        return l;
    }

    private static void styleField(JTextField field) {
        field.setFont(Theme.UI);
        field.setBackground(Theme.INPUT_BG);
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }
}
