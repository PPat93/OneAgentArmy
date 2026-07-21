# OneAgentArmy

A personal AI chat client for Android that talks directly to LLM provider APIs — pay per use instead of paying for a subscription you barely touch.

Built as a private app for personal use. The code is public so it can serve as a reference / portfolio piece, but there are no secrets in this repo — API keys are entered by the user at runtime and stored encrypted on-device.

## Why

Most AI chat subscriptions bundle a flat monthly fee regardless of actual usage. OneAgentArmy skips the middleman: it calls OpenAI, Google Gemini, and Anthropic Claude directly with your own API key, so you only ever pay for the tokens you actually use — while keeping one consistent chat UI across all three.

## Features

- **Three AI providers, one app** — OpenAI (Responses API), Google Gemini (Interactions API), and Anthropic Claude (Messages API), each with three model tiers (cheap / mid / flagship). Every conversation remembers its own model, independent of whichever provider is set as the app-wide default for new chats.
- **Tool calling** — two flavors:
  - Client-side tools with a confirmation card: calendar events, alarms, timers, SMS drafts, navigation, notes. Nothing is sent until you tap confirm, and the tool-call turn itself never touches local storage.
  - Transparent, provider-side round-trips: live weather (Open-Meteo) and web search, invisible to the rest of the app.
- **Hosted web search toggle** — switch between each provider's built-in web search and a Tavily-backed fallback, per your preference; not every model supports hosted search, so the app degrades gracefully where it doesn't.
- **Multimodal attachments** — paste in text/CSV files inline, or attach real photos and PDFs as native multimodal blocks (images auto-scaled to keep costs sane).
- **Cost tracking** — every AI reply shows an estimated cost in EUR (daily ECB exchange rate, no API key required), with running totals per conversation and per month.
- **Pinning & smart sorting** — pin the conversations you're actively using; everything else sorts by most recent message, not creation date.
- **Full-text search** across all conversations, with matches deep-linking straight to the message.
- **Facts / personalization** — save durable facts about yourself once, attach them to conversations that should know about them.
- **Material 3 theming** built entirely from the app icon's own palette.

## Tech stack

- Kotlin + Jetpack Compose (Material 3), MVVM + Repository pattern
- Manual dependency wiring — no DI framework, composition root lives in `AppContainer.kt`
- Room for local persistence (conversations, messages, facts), with additive-only migrations
- OkHttp + kotlinx.serialization — raw HTTP against each provider's REST API rather than an official SDK, so all three providers follow one consistent internal pattern
- DataStore Preferences for settings; API keys encrypted with Android Keystore (AES-256-GCM)
- `minSdk 33`

## Project layout

```
app/src/main/java/com/parrotworks/oneagentarmy/
├── AppContainer.kt              # composition root — manual DI
├── data/                        # Room entities/DAOs, repositories, DataStore
├── model/                       # domain models
├── provider/ai/                 # one package per provider (openai/gemini/anthropic) + shared tool registry
├── tools/                       # client-side confirmation-card tools (calendar, alarms, SMS, ...)
└── ui/                          # Compose screens, per feature (chat, conversationlist, settings, search)
```

## Getting started

This is a personal project, not a published app — there's no build pipeline or Play Store listing to point at. To run it yourself:

1. Open the project in Android Studio (AGP 9 / Kotlin, no extra Kotlin plugin needed — it ships built into AGP 9).
2. Build and install on a device or emulator running API 33+.
3. On first launch, open Settings and paste in your own API key(s) for whichever provider(s) you want to use. Keys never leave the device except as Authorization headers to that provider's API.

## Status

Actively evolving, one small staged branch at a time. See commit history for what's shipped.

---

ParroT woRKs by Piotr Paterek
