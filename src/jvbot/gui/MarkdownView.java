package jvbot.gui;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;

/**
 * Read-only text pane that renders the mini-markdown that chat models
 * produce - hand-written, no libraries:
 *
 *   **bold**   *italic*   `code`   ``` fenced code blocks ```
 *   # / ## / ### headings      - or * bullets  ->  •
 *
 * Also fixes the emoji problem: runs of characters the UI font cannot
 * display are switched to "Segoe UI Emoji" so they render as glyphs
 * instead of empty boxes.
 */
public class MarkdownView extends JTextPane {

    private static final Color CODE_BG = new Color(0x30323B);
    private static final String CODE_FONT = "Consolas";
    private static final String EMOJI_FONT = "Segoe UI Emoji";

    public MarkdownView() {
        setEditable(false);
        setOpaque(false);
        setFont(Theme.CHAT);
        setForeground(Theme.TEXT);
        setCaretColor(Theme.TEXT);
    }

    /** Re-renders the whole content from raw markdown text. */
    public void setMarkdown(String raw) {
        StyledDocument doc = new DefaultStyledDocument();
        try {
            render(doc, raw);
        } catch (BadLocationException cannotHappen) {
            // insertions always append at the end
        }
        setDocument(doc);
    }

    // ------------------------------------------------------------ rendering

    private void render(StyledDocument doc, String raw) throws BadLocationException {
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setFontFamily(normal, Theme.CHAT.getFamily());
        StyleConstants.setFontSize(normal, Theme.CHAT.getSize());
        StyleConstants.setForeground(normal, getForeground());

        String[] lines = raw.split("\n", -1);
        boolean inCodeBlock = false;
        StringBuilder codeBlock = new StringBuilder();

        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];

            // fenced code blocks
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    appendCodeBlock(doc, codeBlock.toString());
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                    codeBlock.setLength(0);
                }
                continue;
            }
            if (inCodeBlock) {
                codeBlock.append(line).append('\n');
                continue;
            }

            // headings
            SimpleAttributeSet base = new SimpleAttributeSet(normal);
            if (line.startsWith("### ")) {
                line = line.substring(4);
                StyleConstants.setBold(base, true);
                StyleConstants.setFontSize(base, Theme.CHAT.getSize() + 1);
            } else if (line.startsWith("## ")) {
                line = line.substring(3);
                StyleConstants.setBold(base, true);
                StyleConstants.setFontSize(base, Theme.CHAT.getSize() + 2);
            } else if (line.startsWith("# ")) {
                line = line.substring(2);
                StyleConstants.setBold(base, true);
                StyleConstants.setFontSize(base, Theme.CHAT.getSize() + 3);
            }

            // bullets: "- item" or "* item" (indent kept)
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            if (indent + 1 < line.length()
                    && (line.charAt(indent) == '-' || line.charAt(indent) == '*')
                    && line.charAt(indent + 1) == ' ') {
                line = line.substring(0, indent) + "• " + line.substring(indent + 2);
            }

            appendInline(doc, line, base);
            if (n < lines.length - 1) {
                insert(doc, "\n", normal);
            }
        }
        if (inCodeBlock) {   // unclosed fence (common mid-stream)
            appendCodeBlock(doc, codeBlock.toString());
        }

        // a little breathing room between lines
        SimpleAttributeSet para = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(para, 0.12f);
        doc.setParagraphAttributes(0, doc.getLength(), para, false);
    }

    /** Handles **bold**, *italic* and `code` inside one line. */
    private void appendInline(StyledDocument doc, String text, SimpleAttributeSet base)
            throws BadLocationException {
        boolean bold = false;
        boolean italic = false;
        StringBuilder run = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '`') {                               // inline code until closing tick
                flush(doc, run, base, bold, italic, false);
                int end = text.indexOf('`', i + 1);
                if (end < 0) end = text.length();
                insert(doc, text.substring(i + 1, end), codeAttrs(base));
                i = end < text.length() ? end + 1 : end;
            } else if (text.startsWith("**", i)) {
                flush(doc, run, base, bold, italic, false);
                bold = !bold;
                i += 2;
            } else if (c == '*'
                    && (italic || (i + 1 < text.length() && text.charAt(i + 1) != ' '))) {
                flush(doc, run, base, bold, italic, false);
                italic = !italic;
                i++;
            } else {
                run.append(c);
                i++;
            }
        }
        flush(doc, run, base, bold, italic, false);
    }

    private void flush(StyledDocument doc, StringBuilder run, SimpleAttributeSet base,
                       boolean bold, boolean italic, boolean code) throws BadLocationException {
        if (run.length() == 0) return;
        SimpleAttributeSet attrs = new SimpleAttributeSet(base);
        if (bold) StyleConstants.setBold(attrs, true);
        if (italic) StyleConstants.setItalic(attrs, true);
        insert(doc, run.toString(), code ? codeAttrs(base) : attrs);
        run.setLength(0);
    }

    private SimpleAttributeSet codeAttrs(SimpleAttributeSet base) {
        SimpleAttributeSet attrs = new SimpleAttributeSet(base);
        StyleConstants.setFontFamily(attrs, CODE_FONT);
        StyleConstants.setBackground(attrs, CODE_BG);
        return attrs;
    }

    private void appendCodeBlock(StyledDocument doc, String code) throws BadLocationException {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, CODE_FONT);
        StyleConstants.setFontSize(attrs, Theme.CHAT.getSize() - 1);
        StyleConstants.setForeground(attrs, new Color(0xDCE0E6));
        StyleConstants.setBackground(attrs, CODE_BG);
        insert(doc, code, attrs);
    }

    /**
     * Inserts text, switching runs the UI font cannot display (emoji,
     * symbols) to the emoji font so they don't render as boxes.
     */
    private void insert(StyledDocument doc, String text, SimpleAttributeSet attrs)
            throws BadLocationException {
        Font uiFont = getFont();
        int i = 0;
        while (i < text.length()) {
            int start = i;
            boolean displayable = uiFont.canDisplay(text.codePointAt(i));
            while (i < text.length()
                    && uiFont.canDisplay(text.codePointAt(i)) == displayable) {
                i += Character.charCount(text.codePointAt(i));
            }
            String chunk = text.substring(start, i);
            if (displayable) {
                doc.insertString(doc.getLength(), chunk, attrs);
            } else {
                SimpleAttributeSet emoji = new SimpleAttributeSet(attrs);
                StyleConstants.setFontFamily(emoji, EMOJI_FONT);
                doc.insertString(doc.getLength(), chunk, emoji);
            }
        }
    }
}
