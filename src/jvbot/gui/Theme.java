package jvbot.gui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** All colors, fonts and reusable custom-painted components for the dark UI. */
public final class Theme {

    private Theme() {}

    // ------------------------------------------------------------- palette
    public static final Color BG          = new Color(0x1E1F24);   // main background
    public static final Color SIDEBAR     = new Color(0x151619);
    public static final Color HEADER      = new Color(0x1A1B20);
    public static final Color USER_CARD   = new Color(0x24344D);   // blue tint
    public static final Color BOT_CARD    = new Color(0x26272E);
    public static final Color ERROR_CARD  = new Color(0x3A2E1E);
    public static final Color INPUT_BG    = new Color(0x26272E);
    public static final Color BORDER      = new Color(0x33353D);
    public static final Color HOVER       = new Color(0x22242B);
    public static final Color SELECTED    = new Color(0x2C2E36);
    public static final Color TEXT        = new Color(0xE8E8EA);
    public static final Color TEXT_DIM    = new Color(0x9A9CA5);
    public static final Color ACCENT      = new Color(0x53B98A);   // green - active / bot
    public static final Color ACCENT_BG   = new Color(0x1E3328);   // pill behind green
    public static final Color WARN        = new Color(0xE0A030);   // amber - errors / no key
    public static final Color WARN_BG     = new Color(0x36301E);   // pill behind amber
    public static final Color PRIMARY     = new Color(0x3D6DEB);   // send / save
    public static final Color DANGER      = new Color(0xE05B5B);   // delete

    // --------------------------------------------------------------- fonts
    public static final Font UI         = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font UI_BOLD    = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font SMALL      = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font CHAT       = new Font("Segoe UI", Font.PLAIN, 15);
    public static final Font TITLE      = new Font("Segoe UI", Font.BOLD, 17);

    /** Multiply a color's brightness (factor > 1 lightens, < 1 darkens). */
    public static Color shade(Color c, double factor) {
        return new Color(
                Math.min(255, (int) (c.getRed() * factor)),
                Math.min(255, (int) (c.getGreen() * factor)),
                Math.min(255, (int) (c.getBlue() * factor)));
    }

    private static void antialias(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    // ---------------------------------------------------------- components

    /** Panel with a rounded filled background; fill may be changed or null. */
    public static class Card extends JPanel {
        private Color fill;
        private final int radius;

        public Card(Color fill) {
            this(fill, 16);
        }

        public Card(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
            setLayout(new BorderLayout());
        }

        public void setFill(Color fill) {
            this.fill = fill;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (fill != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                antialias(g2);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    /** Fully rounded pill button with hover and pressed shades. */
    public static class RoundButton extends JButton {
        private final Color bg;

        public RoundButton(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(UI_BOLD);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            Color fill = bg;
            if (!isEnabled())                 fill = shade(bg, 0.7);
            else if (getModel().isPressed())  fill = shade(bg, 0.85);
            else if (getModel().isRollover()) fill = shade(bg, 1.2);
            g2.setColor(fill);
            int r = getHeight();   // full pill
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Rounded input container whose border glows when focused. */
    public static class InputCard extends JPanel {
        private boolean focused;

        public InputCard() {
            setOpaque(false);
            setLayout(new BorderLayout());
        }

        public void setFocused(boolean value) {
            focused = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(INPUT_BG);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.setColor(focused ? PRIMARY : BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Dark-styled dropdown matching the input fields. */
    public static void styleCombo(JComboBox<?> combo) {
        combo.setFont(UI);
        combo.setBackground(INPUT_BG);
        combo.setForeground(TEXT);
        combo.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g.create();
                        antialias(g2);
                        g2.setColor(TEXT_DIM);
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        // small filled down-triangle, drawn - not a font glyph
                        g2.fillPolygon(new int[]{cx - 4, cx + 4, cx},
                                       new int[]{cy - 2, cy - 2, cy + 3}, 3);
                        g2.dispose();
                    }
                };
                b.setBackground(INPUT_BG);
                b.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                b.setContentAreaFilled(false);
                b.setFocusPainted(false);
                b.setOpaque(true);
                return b;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                // the default paints a light background - keep it dark
                g.setColor(INPUT_BG);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override
            public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
                // force the non-focused colors (combo bg/fg) in all states
                super.paintCurrentValue(g, bounds, false);
            }
        });
        // the popup list behind the renderer must be dark too
        try {
            Object popup = combo.getAccessibleContext().getAccessibleChild(0);
            if (popup instanceof javax.swing.plaf.basic.BasicComboPopup) {
                JList<?> list = ((javax.swing.plaf.basic.BasicComboPopup) popup).getList();
                list.setBackground(INPUT_BG);
                list.setSelectionBackground(SELECTED);
                ((javax.swing.plaf.basic.BasicComboPopup) popup)
                        .setBorder(BorderFactory.createLineBorder(BORDER));
            }
        } catch (RuntimeException lookAndFeelQuirk) {
            // popup styling is cosmetic - ignore if the LAF doesn't expose it
        }
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                l.setFont(UI);
                l.setOpaque(true);
                l.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                l.setBackground(selected ? SELECTED : INPUT_BG);
                l.setForeground(selected ? TEXT : TEXT_DIM);
                return l;
            }
        });
        if (combo.getEditor() != null
                && combo.getEditor().getEditorComponent() instanceof JTextField) {
            JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
            editor.setFont(UI);
            editor.setBackground(INPUT_BG);
            editor.setForeground(TEXT);
            editor.setCaretColor(TEXT);
            editor.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 4));
        }
    }

    /** Slim dark scrollbar - the Windows default would look out of place. */
    public static void styleScrollBar(JScrollBar bar, Color track) {
        bar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(0x3E414C);
                trackColor = track;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) { return invisibleButton(); }

            @Override
            protected JButton createIncreaseButton(int orientation) { return invisibleButton(); }

            private JButton invisibleButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = (Graphics2D) g.create();
                antialias(g2);
                g2.setColor(thumbColor);
                g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
                g.setColor(trackColor);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });
        bar.setPreferredSize(new Dimension(10, 10));
        bar.setBackground(track);
    }
}
