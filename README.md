# JVBot — AI Chatbot in Java

A lite Claude-style AI chatbot **desktop app** (Java Swing) with a terminal
version included, written in **pure Java with zero external libraries** — no
Maven, no Gradle, no frameworks, no database. The bot is a thin wrapper
around the free NVIDIA API (build.nvidia.com) with a small fast model
(**Gemma**) by default.

## Features

- **Modern desktop UI (like Claude chat)** — dark theme, sidebar with
  **+ New Chat**, click any saved chat to **continue** it, hover and press ✕
  to **delete** it, Settings dialog for the API key, responsive layout
- **Markdown replies** — `**bold**`, *italic*, `code`, code blocks, bullets
  and headings render properly (hand-written renderer, no libraries)
- **Saved models with their own keys** — in Settings you add model+key
  pairs; the header dropdown lists exactly the models YOU added, and
  switching one also switches to its key (great when free keys run out of
  credits). The full model catalog (`/v1/models`) loads automatically in
  Settings to help you pick.
- **AI chat via API wrapper** — calls NVIDIA's OpenAI-compatible endpoint
  (`https://integrate.api.nvidia.com/v1/chat/completions`)
- **Streaming replies** — the answer appears word-by-word (SSE), like Claude
- **Activation by key** — the bot stays inactive until you add your API key in
  **Settings**; it is remembered in `settings.properties`
- **Folder-based chat history** — every conversation is saved as a readable
  JSON file in the `chats\` folder (no database)
- **Conversation memory** — the whole chat is sent each turn, so the bot
  remembers context
- **Hand-written JSON parser/writer** (`Json.java`) — no third-party libraries

## Getting a free API key

1. Go to <https://build.nvidia.com> and sign up (free).
2. Open any model page (e.g. *gemma-3n-e4b-it*) and click **Get API Key**.
3. Copy the key — it starts with `nvapi-`.

## Running

Requires only a JDK (11 or newer).

- **`run.bat`** — compiles everything and opens the desktop app. On first
  run, open **Settings**, paste your key, Save — the bot activates.
- **`run-console.bat`** — the same bot in the terminal.

Both share the same key, model, and chat history.

## Console commands

| Command | What it does |
|---|---|
| `/key <key>` | Save your NVIDIA API key (activates the bot) |
| `/key` | Show all saved keys (masked, active marked) |
| `/model <name>` | Switch model (default `google/gemma-3n-e4b-it`) |
| `/models` | List all models available on the API |
| `/new` | Save the current chat and start a fresh one |
| `/history` | List saved chats in the `chats\` folder |
| `/load <n>` | Resume chat number *n* from `/history` |
| `/help` | Show the command list |
| `/exit` | Save and quit |

Anything that doesn't start with `/` is sent to the AI.

Other small models to try with `/model` (browse the full list at build.nvidia.com):
`meta/llama-3.1-8b-instruct`, `microsoft/phi-3-mini-4k-instruct`

## Project structure

```
src\jvbot\
  Main.java          console entry point: input loop, commands, activation gate
  Settings.java      loads/saves api key + model in settings.properties
  NvidiaClient.java  the AI wrapper: HTTPS call + SSE streaming (via callback)
  ChatSession.java   current conversation, autosaves after every reply
  HistoryStore.java  saves/loads/lists chats as JSON files in chats\
  Message.java       one chat message (role + content)
  Json.java          minimal hand-written JSON writer + parser
src\jvbot\gui\
  GuiMain.java       desktop app: frame, sidebar, header, send flow
  ChatPanel.java     scrollable message cards with live streaming
  SettingsDialog.java window where the API key is added (activates the bot)
  Theme.java         dark palette, fonts, rounded components, scrollbars
chats\               chat history folder (created automatically, shared)
settings.properties  created when you save a key
run.bat              compile + run the desktop app
run-console.bat      compile + run the terminal version
```

## Notes

- The API key is stored as plain text in `settings.properties` — fine for a
  college project, but don't commit that file anywhere public.
- Chat files autosave after every bot reply, so nothing is lost on Ctrl+C.
