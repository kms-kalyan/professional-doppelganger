# How to Get a HuggingFace API Key

Follow these steps to get your free HuggingFace API key:

## Step 1: Create a HuggingFace Account

1. Go to https://huggingface.co/join
2. Sign up for a free account using:
   - Email address, or
   - GitHub account, or
   - Google account
3. Verify your email if required

## Step 2: Generate an API Token

1. After logging in, go to: https://huggingface.co/settings/tokens
   - Or click on your profile picture (top right) → Settings → Access Tokens

2. Click **"New token"** button

3. Fill in the form:
   - **Token name**: Give it a descriptive name (e.g., "Akka Cluster Project")
   - **Type**: Select **"Read"** (this is enough for using the Inference API)
   - **Expiration**: Choose "No expiration" (or set a date if you prefer)

4. Click **"Generate token"**

5. **IMPORTANT**: Copy the token immediately! It will look like:
   ```
   hf_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
   - You won't be able to see it again after closing the page
   - If you lose it, you'll need to create a new one

## Step 3: Use the Token in Your Application

### Option A: Update the startup script

Edit `scripts/start-node1.sh`:
```bash
mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 hf_YOUR_ACTUAL_TOKEN_HERE google/flan-t5-large" \
    -Dakka.remote.artery.canonical.port=2551
```

Replace `hf_YOUR_ACTUAL_TOKEN_HERE` with your actual token (it should start with `hf_`).

### Option B: Use environment variable (more secure)

1. Set the token as an environment variable:
   ```bash
   export HF_API_KEY="hf_your_actual_token_here"
   ```

2. Update the script to use it:
   ```bash
   mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
       -Dexec.args="2551 8080 ${HF_API_KEY} google/flan-t5-large" \
       -Dakka.remote.artery.canonical.port=2551
   ```

## Step 4: Test Without API Key (Optional)

You can also use `"none"` as the API key for public models, but you'll be rate-limited:

```bash
mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 none google/flan-t5-large" \
    -Dakka.remote.artery.canonical.port=2551
```

**Note**: Using "none" will have stricter rate limits, so getting a free API key is recommended.

## Troubleshooting

### "Invalid API key" error
- Make sure you copied the entire token (it should start with `hf_`)
- Check for extra spaces or newlines
- Verify the token hasn't expired (if you set an expiration date)

### "Rate limit exceeded" error
- Free tier has rate limits
- Wait a few minutes and try again
- Consider using a different model

### "Model is loading" error (503)
- Some models need to be "woken up" if they haven't been used recently
- Wait 10-30 seconds and try again
- The model will stay loaded for a while after first use

## Popular Free Models to Try

- `google/flan-t5-large` - Good for general tasks (default)
- `google/flan-t5-base` - Smaller, faster version
- `microsoft/DialoGPT-medium` - Conversational AI
- `facebook/blenderbot-400M-distill` - Chatbot
- `tiiuae/falcon-7b-instruct` - Instruction following

## Security Tips

- **Never commit your API key to Git!**
- Use environment variables or configuration files that are in `.gitignore`
- If you accidentally commit a key, revoke it immediately and create a new one
- Use "Read" tokens (not "Write") for this application

## Need Help?

- HuggingFace Documentation: https://huggingface.co/docs/api-inference/index
- HuggingFace Community: https://huggingface.co/discuss

