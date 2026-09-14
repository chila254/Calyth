# Calyth

AI-powered chat assistant with multi-model support (Groq & Gemini).

## Features

- Multi-model support: GPT-OSS, Qwen, Allam, Gemini
- Streaming responses
- Markdown rendering with code highlighting
- Image upload & vision analysis
- Web search via Groq Compound
- Conversation sharing with public links
- Voice input (web)
- Dark/Light theme
- Chat folders & organization
- Edit, regenerate & copy messages
- Export as Markdown

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin, Ktor 2.3.5, Gradle 8.5 |
| Web | Vanilla JS, CSS Variables |
| Android | Kotlin, Jetpack Compose, Material3 |
| APIs | Groq, Google Gemini |
| Deploy | Render (backend), GitHub Pages (frontend) |

## Quick Start

### Web
Open [https://chila254.github.io/Calyth/](https://chila254.github.io/Calyth/)

### Backend
```bash
# Set your API key
echo "GROQ_API_KEY=gsk_..." > .env

# Run
./gradlew run
```

### Android
```bash
cd android
./gradlew assembleDebug
```

## Configuration

| Variable | Description |
|----------|-------------|
| `GROQ_API_KEY` | Required for Groq models |
| `GEMINI_API_KEY` | Required for Gemini models |
| `PORT` | Server port (default: 8082) |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check |
| `GET` | `/models` | List available models |
| `POST` | `/chat` | Send a message |
| `POST` | `/chat/stream` | Streaming chat (SSE) |
| `POST` | `/chat/history` | Chat with conversation history |
| `POST` | `/search` | Web search via Groq Compound |
| `POST` | `/upload` | Image upload for vision |
| `POST` | `/share` | Share a conversation |
| `GET` | `/shared/:id/html` | View shared conversation |

## License

MIT
