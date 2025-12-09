# Groq Rate Limit Information

## Understanding Rate Limits

Groq's free tier has rate limits to ensure fair usage. You may encounter rate limit errors when making too many requests in a short period.

## What Happens When Rate Limited?

When you hit the rate limit, you'll see an error message:
```
Error: Groq API rate limit exceeded. Please wait 10-30 seconds and try again.
```

## Solutions

### 1. Wait and Retry (Recommended)
- **Wait 10-30 seconds** between requests
- The error message will tell you exactly how long to wait
- Simply try your query again after waiting

### 2. Upgrade Your Account
- Free tier: Limited requests per minute
- Paid tier: Higher rate limits
- Visit https://console.groq.com/ to upgrade

### 3. Optimize Your Usage
- Don't send multiple rapid requests
- Batch related questions together
- Use the chatbot conversationally (not rapid-fire)

## Rate Limit Details

**Free Tier Limits:**
- Requests per minute: Limited (varies)
- Requests per day: Generous free allowance
- No credit card required

**What This Means:**
- ✅ Perfect for testing and personal use
- ✅ Great for occasional queries
- ⚠️ May hit limits with rapid requests
- 💡 Wait a few seconds between requests

## Best Practices

1. **Take Your Time**: Don't rush - wait a moment between questions
2. **Read Responses**: Take time to read the chatbot's answers
3. **Natural Conversation**: Use it like a real conversation, not a speed test

## Troubleshooting

**Q: I keep getting rate limit errors**
- A: Wait longer between requests (30-60 seconds)

**Q: Can I increase the rate limit?**
- A: Yes, upgrade to a paid plan at https://console.groq.com/

**Q: Is there a way to bypass rate limits?**
- A: No, but waiting between requests is the best approach

## Alternative: Use HuggingFace

If Groq rate limits are too restrictive, you can switch to HuggingFace:

```bash
# Edit scripts/start-node1.sh
-Dexec.args="2551 8080 hf_YOUR_HF_KEY model-name huggingface"
```

Note: HuggingFace may be slower but has different rate limits.

## Summary

Rate limits are normal on free tiers. Just wait a moment between requests and you'll be fine! The chatbot will work great for normal conversational use.

