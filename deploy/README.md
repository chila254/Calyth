# Calyth — Render Deployment Guide

## Quick Deploy (1 click)

1. Push your code to GitHub
2. Go to [render.com](https://render.com) → **New** → **Blueprint**
3. Connect your `chila254/Calyth` repo
4. Render auto-detects `render.yaml` and creates the service
5. Add your API keys in **Environment** tab:
   - `GROQ_API_KEY` = your Groq key
   - `GEMINI_API_KEY` = your Gemini key (optional)
6. Deploy

Your app will be live at `https://calyth.onrender.com`

## Environment Variables

| Key | Required | Where to get |
|---|---|---|
| `GROQ_API_KEY` | Yes | [console.groq.com/keys](https://console.groq.com/keys) |
| `GEMINI_API_KEY` | No | [aistudio.google.com](https://aistudio.google.com) |

## Free Tier Notes

- Render free tier spins down after 15 min of inactivity
- First request after spin-down takes ~30s
- Good for demo/personal use

## Update Android App

In the app settings, set server URL to:
```
https://calyth.onrender.com
```

## Commands

- **Manual deploy**: Push to GitHub, Render auto-deploys
- **Logs**: Render dashboard → Logs tab
- **Restart**: Render dashboard → Manual Deploy → Clear build cache & deploy
