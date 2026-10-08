package jvbot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/**
 * JVBot - a lite Claude-Code-style AI chatbot for the terminal.
 *
 * Flow: on start we load settings.properties. If no API key is saved yet the
 * bot is INACTIVE - it only accepts commands and tells the user to run
 * /key <key> with a free key from https://build.nvidia.com. Once a key is
 * saved the bot activates and normal messages go to the model.
 */
public class Main {

    // ANSI colors - render in Windows Terminal / VS Code; harmless elsewhere.
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String DIM    = "\u001B[90m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RESET  = "\u001B[0m";

    private final Settings settings;
    private final HistoryStore store;
    private final NvidiaClient client;
    private ChatSession session;

    public static void main(String[] args) {
        Path base = Paths.get("").toAbsolutePath();
        new Main(base).run();
    }

    private Main(Path base) {
        settings = new Settings(base.resolve("settings.properties"));
        store = new HistoryStore(base.resolve("chats"));
        client = new NvidiaClient(settings);
        session = new ChatSession(store);
    }

    private void run() {
        printBanner();
        if (settings.hasKey()) {
            System.out.println(GREEN + " API key loaded - bot is ACTIVE. Type a message, or /help for commands." + RESET);
        } else {
            printActivationHelp();
        }

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print(CYAN + BOLD + "\n you > " + RESET);
            if (!in.hasNextLine()) break;                 // Ctrl+Z / end of input
            // strip a UTF-8 BOM (appears when input is piped in on Windows)
            String line = in.nextLine().replace("\uFEFF", "").trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("/")) {
                if (!handleCommand(line)) break;          // /exit returns false
            } else {
                chat(line);
            }
        }
        session.save(settings.getModel());
        System.out.println(DIM + " Chat saved. Bye!" + RESET);
    }

    // ------------------------------------------------------------- commands

    /** Returns false only for /exit. */
    private boolean handleCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "/help":    printHelp(); break;
            case "/key":     cmdKey(arg); break;
            case "/model":   cmdModel(arg); break;
            case "/models":  cmdModels(); break;
            case "/new":     cmdNew(); break;
            case "/history": cmdHistory(); break;
            case "/load":    cmdLoad(arg); break;
            case "/exit":
            case "/quit":    return false;
            default:
                System.out.println(YELLOW + " Unknown command: " + cmd + "  (try /help)" + RESET);
        }
        return true;
    }

    private void cmdKey(String arg) {
        if (arg.isEmpty()) {
            List<Settings.Profile> profiles = settings.getProfiles();
            if (profiles.isEmpty()) {
                printActivationHelp();
            } else {
                System.out.println(" Saved models (each with its own key):");
                for (int i = 0; i < profiles.size(); i++) {
                    boolean active = i == settings.getActiveProfile();
                    System.out.println("   " + profiles.get(i).model
                            + "  " + mask(profiles.get(i).key)
                            + (active ? "   <- active" : ""));
                }
            }
            return;
        }
        boolean firstKey = !settings.hasKey();
        if (!arg.startsWith("nvapi-")) {
            System.out.println(YELLOW + " [note] NVIDIA keys usually start with 'nvapi-'. Saving it anyway." + RESET);
        }
        settings.setKey(arg);
        if (firstKey) {
            System.out.println(GREEN + " [ok] Key saved. Bot ACTIVATED - say hello!" + RESET);
        } else {
            System.out.println(GREEN + " [ok] Key updated." + RESET);
        }
    }

    private void cmdModel(String arg) {
        if (arg.isEmpty()) {
            System.out.println(" Current model: " + settings.getModel());
            System.out.println(DIM + " Change with /model <name>, e.g. /model meta/llama-3.1-8b-instruct" + RESET);
            return;
        }
        settings.setModel(arg);
        System.out.println(GREEN + " [ok] Model set to " + arg + RESET);
    }

    private void cmdModels() {
        if (!settings.hasKey()) {
            printActivationHelp();
            return;
        }
        System.out.println(" Loading models from the API...");
        try {
            List<String> models = client.listModels();
            int shown = Math.min(models.size(), 30);
            for (int i = 0; i < shown; i++) {
                System.out.printf("  %2d. %s%n", i + 1, models.get(i));
            }
            if (models.size() > shown) {
                System.out.println(DIM + "  ...and " + (models.size() - shown) + " more" + RESET);
            }
            System.out.println(DIM + " Switch with /model <name>" + RESET);
        } catch (NvidiaClient.ApiException e) {
            System.out.println(YELLOW + " [error] " + e.getMessage() + RESET);
        }
    }

    private void cmdNew() {
        session.save(settings.getModel());
        if (!session.isEmpty()) {
            System.out.println(DIM + " Saved " + session.fileName() + RESET);
        }
        session = new ChatSession(store);
        System.out.println(GREEN + " [ok] Started a new chat." + RESET);
    }

    private void cmdHistory() {
        List<Path> files = store.list();
        if (files.isEmpty()) {
            System.out.println(" No saved chats yet.");
            return;
        }
        System.out.println(" Saved chats (newest first):");
        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            System.out.printf("  %2d. %s  (%d messages)%n",
                    i + 1, f.getFileName(), store.messageCount(f));
        }
        System.out.println(DIM + " Resume one with /load <number>" + RESET);
    }

    private void cmdLoad(String arg) {
        List<Path> files = store.list();
        int n;
        try {
            n = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            System.out.println(YELLOW + " Usage: /load <number>   (see /history for numbers)" + RESET);
            return;
        }
        if (n < 1 || n > files.size()) {
            System.out.println(YELLOW + " No chat #" + n + " - run /history to see the list." + RESET);
            return;
        }
        session.save(settings.getModel());              // don't lose the current chat
        Path chosen = files.get(n - 1);
        session = ChatSession.resume(store, chosen);
        System.out.println(GREEN + " [ok] Loaded " + chosen.getFileName()
                + " (" + session.messages().size() + " messages). Conversation continues." + RESET);
    }

    // ----------------------------------------------------------------- chat

    private void chat(String text) {
        if (!settings.hasKey()) {
            System.out.println(YELLOW + " Bot is not active yet - no API key set." + RESET);
            printActivationHelp();
            return;
        }
        session.add("user", text);
        System.out.print(GREEN + BOLD + "\n bot > " + RESET);
        try {
            String reply = client.chat(session.messages(), piece -> {
                System.out.print(piece);
                System.out.flush();
            });
            System.out.println();
            if (reply.isEmpty()) {
                System.out.println(DIM + " (the model returned an empty reply)" + RESET);
            }
            session.add("assistant", reply);
            session.save(settings.getModel());          // autosave after every reply
        } catch (NvidiaClient.ApiException e) {
            System.out.println();
            System.out.println(YELLOW + " [error] " + e.getMessage() + RESET);
            session.removeLast();                        // keep history consistent
        }
    }

    // ------------------------------------------------------------------- ui

    private void printBanner() {
        System.out.println(CYAN + "==========================================" + RESET);
        System.out.println(CYAN + "   JVBot - AI Chatbot in Java" + RESET);
        System.out.println(CYAN + "   model: " + settings.getModel() + RESET);
        System.out.println(CYAN + "==========================================" + RESET);
    }

    private void printActivationHelp() {
        System.out.println(YELLOW + " No API key set. Get a FREE key at https://build.nvidia.com" + RESET);
        System.out.println(YELLOW + " then activate the bot with:  /key <your-key>" + RESET);
    }

    private void printHelp() {
        System.out.println(" Commands:");
        System.out.println("   /key <key>      save your NVIDIA API key (activates the bot)");
        System.out.println("   /key            show the saved key (masked)");
        System.out.println("   /model <name>   switch model (current: " + settings.getModel() + ")");
        System.out.println("   /models         list all models available on the API");
        System.out.println("   /new            save this chat and start a fresh one");
        System.out.println("   /history        list saved chats in the chats folder");
        System.out.println("   /load <n>       resume chat number n from /history");
        System.out.println("   /exit           save and quit");
        System.out.println(" Anything else you type is sent to the AI.");
    }

    private static String mask(String key) {
        if (key.length() <= 10) return "**********";
        return key.substring(0, 9) + "..." + key.substring(key.length() - 4);
    }
}
