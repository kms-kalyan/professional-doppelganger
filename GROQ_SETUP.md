# Groq Setup Guide (Recommended - Free & Fast!)

Groq is a **much better alternative** to HuggingFace - it's faster and more reliable!

## Why Groq?

✅ **Very Fast** - Responses in seconds  
✅ **Generous Free Tier** - No credit card required  
✅ **Reliable** - No 404 errors or endpoint issues  
✅ **Easy Setup** - Simple API key  
✅ **Great Models** - Llama, Mixtral, Gemma

## Quick Setup

### Step 1: Get Free API Key

1. Go to https://console.groq.com/
2. Sign up for a free account (email or Google)
3. Navigate to **API Keys** section
4. Click **"Create API Key"**
5. Copy your key (starts with `gsk_`)

### Step 2: Update Startup Script

Edit `scripts/start-node1.sh`:

```bash
mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 gsk_YOUR_ACTUAL_GROQ_KEY llama-3.1-8b-instant groq" \
    -Dakka.remote.artery.canonical.port=2551
```

Replace `gsk_YOUR_ACTUAL_GROQ_KEY` with your actual Groq API key.

### Step 3: Start the Application

```bash
./scripts/start-node1.sh
```

Or use the dedicated Groq script:
```bash
./scripts/start-node1-groq.sh
```

## Popular Groq Models

- `llama-3.1-8b-instant` - Fast, smaller (default, recommended)
- `mixtral-8x7b-32768` - Mixture of experts, larger context
- `gemma-7b-it` - Google's Gemma model
- `llama-3.2-3b-instant` - Very fast, smaller model

**Note:** `llama-3.1-70b-versatile` has been decommissioned. Use `llama-3.1-8b-instant` instead.

## Comparison

| Feature | Groq | HuggingFace |
|---------|------|-------------|
| Speed | ⚡ Very Fast | 🐢 Slow |
| Reliability | ✅ Stable | ⚠️ Endpoint issues |
| Free Tier | ✅ Generous | ✅ Free (but problematic) |
| Setup | ✅ Easy | ⚠️ Complex |

## Troubleshooting

### "Invalid API key" error
- Make sure you copied the entire key (starts with `gsk_`)
- Verify the key is active at https://console.groq.com/keys

### "Rate limit exceeded" error
- Free tier has rate limits
- Wait a few minutes and try again
- Consider upgrading if you need higher limits

## That's It!

Groq is much simpler and more reliable than HuggingFace. Your professional doppelganger chatbot will work great with Groq!

