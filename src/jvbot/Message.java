package jvbot;

/** One chat message: role is "user" or "assistant". */
public class Message {
    public final String role;
    public final String content;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
