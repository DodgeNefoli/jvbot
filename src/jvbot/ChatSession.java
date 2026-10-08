package jvbot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The conversation currently on screen. Keeps the full message list in
 * memory (so the bot remembers context) and autosaves it after each reply.
 */
public class ChatSession {

    private final HistoryStore store;
    private final List<Message> messages = new ArrayList<>();
    private Path file; // null until the first save

    public ChatSession(HistoryStore store) {
        this.store = store;
    }

    /** Reopen an old chat file and continue the conversation. */
    public static ChatSession resume(HistoryStore store, Path file) {
        ChatSession s = new ChatSession(store);
        s.file = file;
        s.messages.addAll(store.load(file));
        return s;
    }

    public List<Message> messages() {
        return Collections.unmodifiableList(messages);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public void add(String role, String content) {
        messages.add(new Message(role, content));
    }

    /** Drop the last message (used when a request fails and got no reply). */
    public void removeLast() {
        if (!messages.isEmpty()) {
            messages.remove(messages.size() - 1);
        }
    }

    public void save(String model) {
        if (messages.isEmpty()) return;
        if (file == null) file = store.newSessionFile();
        store.save(file, model, messages);
    }

    public String fileName() {
        return file == null ? "(not saved yet)" : file.getFileName().toString();
    }
}
