package jvbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Saves and loads chat sessions as JSON files in the chats\ folder.
 * One file per session - no database anywhere.
 */
public class HistoryStore {

    private final Path dir;

    public HistoryStore(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("[warn] Could not create chats folder: " + e.getMessage());
        }
    }

    /** New file named by timestamp, e.g. chat-2026-07-14_153000.json */
    public Path newSessionFile() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        return dir.resolve("chat-" + stamp + ".json");
    }

    public void save(Path file, String model, List<Message> messages) {
        List<Object> msgs = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", m.role);
            mm.put("content", m.content);
            msgs.add(mm);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("created", file.getFileName().toString());
        root.put("model", model);
        root.put("messages", msgs);
        try {
            Files.write(file, Json.writePretty(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[warn] Could not save chat: " + e.getMessage());
        }
    }

    /** All saved chat files, newest first. */
    public List<Path> list() {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                  .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                  .forEach(files::add);
        } catch (IOException e) {
            System.err.println("[warn] Could not list chats: " + e.getMessage());
        }
        return files;
    }

    public List<Message> load(Path file) {
        List<Message> messages = new ArrayList<>();
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Object root = Json.parse(text);
            Object msgs = Json.get(root, "messages");
            if (msgs instanceof List) {
                for (Object item : (List<?>) msgs) {
                    Object role = Json.get(item, "role");
                    Object content = Json.get(item, "content");
                    if (role instanceof String && content instanceof String) {
                        messages.add(new Message((String) role, (String) content));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[warn] Could not load chat " + file.getFileName() + ": " + e.getMessage());
        }
        return messages;
    }

    /** Message count for the /history listing (0 if the file is unreadable). */
    public int messageCount(Path file) {
        return load(file).size();
    }

    /** Delete a saved chat file. Returns true if it existed and was removed. */
    public boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("[warn] Could not delete chat " + file.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Short title for a chat: its first user message (used by the GUI sidebar). */
    public String title(Path file) {
        for (Message m : load(file)) {
            if ("user".equals(m.role)) {
                String t = m.content.replaceAll("\\s+", " ").trim();
                return t.length() > 34 ? t.substring(0, 31) + "..." : t;
            }
        }
        return file.getFileName().toString();
    }
}
