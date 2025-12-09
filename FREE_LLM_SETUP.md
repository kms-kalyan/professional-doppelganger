# Free LLM Setup Guide

This guide shows you how to use free LLM alternatives instead of OpenAI.

**The application supports 3 providers:**
- **OpenAI** (default, requires paid API)
- **Groq** (FREE - Fast inference)
- **HuggingFace** (FREE - Many open-source models)

You choose which provider to use when starting the application.

## Option 1: HuggingFace (Recommended - Free & Open Source)

HuggingFace offers free access to many open-source models through their Inference API.

### Steps:

1. **Get a free API key (optional but recommended):**
   - Go to https://huggingface.co/settings/tokens
   - Create a free account
   - Generate a new token (read access is enough)
   - Note: You can use "none" as API key for public models, but you'll be rate-limited

2. **Start the application with HuggingFace:**
   ```bash
   ./scripts/start-node1-huggingface.sh
   ```
   
   Or manually:
   ```bash
   mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
       -Dexec.args="2551 8080 hf_YOUR_API_KEY google/flan-t5-large huggingface"
   ```

3. **Popular free HuggingFace models:**
   - `google/flan-t5-large` - Good for general tasks
   - `microsoft/DialoGPT-medium` - Conversational AI
   - `facebook/blenderbot-400M-distill` - Chatbot
   - `google/flan-t5-base` - Smaller, faster
   - `tiiuae/falcon-7b-instruct` - Instruction following

## Option 2: Groq (Fast & Free)

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

## Option 3: Ollama (Local - Completely Free)

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

## Option 4: OpenAI (Default - Requires Paid API)

OpenAI does have a free tier, but it's limited:
- Limited requests per day
- Rate limits
- Requires billing setup for higher usage

## Quick Comparison

| Provider | Free Tier | Speed | Setup Difficulty | Models Available |
|----------|-----------|-------|------------------|------------------|
| **HuggingFace** | ✅ Free (rate limited) | 🐢 Medium | Easy | Thousands of models |
| **Groq** | ✅ Generous | ⚡ Very Fast | Easy | Limited selection |
| **Ollama** | ✅ Unlimited | ⚡ Fast (local) | Medium | Many models (local) |
| **OpenAI** | ⚠️ Very Limited | 🐢 Medium | Easy | GPT models only |

## Recommended: HuggingFace

For your project, I recommend **HuggingFace** because:
- ✅ Completely free (with rate limits)
- ✅ Thousands of open-source models to choose from
- ✅ No billing required
- ✅ Easy to switch between models
- ✅ Great for learning and experimentation

**To use HuggingFace:**
1. Get a free API key from https://huggingface.co/settings/tokens (or use "none")
2. Run: `./scripts/start-node1-huggingface.sh`
3. Or manually specify `huggingface` as the provider in the command line

