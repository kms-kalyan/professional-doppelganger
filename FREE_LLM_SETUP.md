# Free LLM Setup Guide

This guide shows you how to use free LLM alternatives instead of OpenAI.

**The application supports 2 providers:**
- **Groq** (FREE - Fast inference, recommended)
- **OpenAI** (requires paid API)

You choose which provider to use when starting the application.

## Option 1: Groq (Fast & Free - Recommended)

### Steps:

1. **Get a free API key:**
   - Go to https://console.groq.com/
   - Sign up for a free account
   - Navigate to API Keys section
   - Create a new API key

2. **Start the application with Groq:**
   ```bash
   ./scripts/start-node1-groq.sh
   ```
   
   Or manually:
   ```bash
   mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
       -Dexec.args="2551 8080 gsk_YOUR_API_KEY llama-3.1-70b-versatile groq"
   ```

3. **Popular Groq models:**
   - `llama-3.1-70b-versatile` - Large, versatile
   - `mixtral-8x7b-32768` - Mixture of experts
   - `gemma-7b-it` - Google's Gemma model

## Option 2: Ollama (Local - Completely Free)

Run models locally on your machine.

1. **Install Ollama:**
   ```bash
   # macOS
   brew install ollama
   
   # Or download from https://ollama.ai
   ```

2. **Start Ollama service:**
   ```bash
   ollama serve
   ```

3. **Pull a model:**
   ```bash
   ollama pull llama2
   # or
   ollama pull mistral
   ```

4. **Create LLMActorOllama.java** that calls `http://localhost:11434/api/generate`

## Option 3: OpenAI (Requires Paid API)

OpenAI does have a free tier, but it's limited:
- Limited requests per day
- Rate limits
- Requires billing setup for higher usage

## Quick Comparison

| Provider | Free Tier | Speed | Setup Difficulty | Models Available |
|----------|-----------|-------|------------------|------------------|
| **Groq** | ✅ Generous | ⚡ Very Fast | Easy | Limited selection |
| **Ollama** | ✅ Unlimited | ⚡ Fast (local) | Medium | Many models (local) |
| **OpenAI** | ⚠️ Very Limited | 🐢 Medium | Easy | GPT models only |

## Recommended: Groq

For your project, I recommend **Groq** because:
- ✅ Generous free tier
- ✅ Very fast inference
- ✅ Easy setup
- ✅ No billing required for free tier
- ✅ Great for learning and experimentation

**To use Groq:**
1. Get a free API key from https://console.groq.com/
2. Run: `./scripts/start-node1.sh`
3. Or manually specify `groq` as the provider in the command line

