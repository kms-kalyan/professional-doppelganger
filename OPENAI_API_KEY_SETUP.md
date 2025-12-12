# How to Get an OpenAI API Key

Follow these steps to get your OpenAI API key:

## Step 1: Create an OpenAI Account

1. Go to https://platform.openai.com/signup
2. Sign up for an account using:
   - Email address, or
   - Google account, or
   - Microsoft account
3. Verify your email if required

## Step 2: Add Payment Method (Required)

OpenAI requires a payment method to be added to your account, even if you're using the free tier credits:
1. Go to https://platform.openai.com/account/billing
2. Click "Add payment method"
3. Add a credit card or other payment method
4. Note: You may receive free credits when you first sign up

## Step 3: Generate an API Key

1. After logging in, go to: https://platform.openai.com/api-keys
   - Or navigate: Profile → API Keys

2. Click **"Create new secret key"** button

3. Fill in the form:
   - **Name**: Give it a descriptive name (e.g., "Akka Cluster Project")
   - **Permissions**: Leave default (full access)

4. Click **"Create secret key"**

5. **IMPORTANT**: Copy the token immediately! It will look like:
   ```
   sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
   - You won't be able to see it again after closing the page
   - If you lose it, you'll need to create a new one

## Step 4: Use the Token in Your Application

### Option A: Update the startup script

Edit `scripts/start-node1.sh` or create a new script:
```bash
mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 sk_YOUR_ACTUAL_TOKEN_HERE gpt-3.5-turbo openai" \
    -Dakka.remote.artery.canonical.port=2551
```

Replace `sk_YOUR_ACTUAL_TOKEN_HERE` with your actual token (it should start with `sk-`).

### Option B: Use environment variable (more secure)

1. Set the token as an environment variable:
   ```bash
   export OPENAI_API_KEY="sk_your_actual_token_here"
   ```

2. Update the script to use it:
   ```bash
   mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
       -Dexec.args="2551 8080 ${OPENAI_API_KEY} gpt-3.5-turbo openai" \
       -Dakka.remote.artery.canonical.port=2551
   ```

## Step 5: Check Your Usage and Credits

1. Go to https://platform.openai.com/usage
2. Monitor your API usage and remaining credits
3. Set up usage limits if needed: https://platform.openai.com/account/billing/limits

## Troubleshooting

### "Invalid API key" error
- Make sure you copied the entire token (it should start with `sk-`)
- Check for extra spaces or newlines
- Verify the token hasn't been revoked (check at https://platform.openai.com/api-keys)

### "Insufficient quota" error
- Check your account balance at https://platform.openai.com/account/billing
- Add credits to your account if needed
- Verify your payment method is valid

### "Rate limit exceeded" error
- Free tier has rate limits
- Wait a few minutes and try again
- Consider upgrading your tier for higher limits
- Check your usage limits at https://platform.openai.com/account/billing/limits

### "Model not found" error
- Verify the model name is correct
- Check available models at https://platform.openai.com/docs/models
- Popular models: `gpt-3.5-turbo`, `gpt-4`, `gpt-4-turbo`

## Popular Models to Try

- `gpt-3.5-turbo` - Fast and cost-effective (recommended for most use cases)
- `gpt-4` - More capable, slower, more expensive
- `gpt-4-turbo` - Latest GPT-4 with improvements
- `gpt-4o` - Latest model with multimodal capabilities

## Pricing Information

OpenAI charges based on usage:
- **gpt-3.5-turbo**: ~$0.50 per 1M input tokens, ~$1.50 per 1M output tokens
- **gpt-4**: ~$30 per 1M input tokens, ~$60 per 1M output tokens
- **gpt-4-turbo**: ~$10 per 1M input tokens, ~$30 per 1M output tokens

Check current pricing at: https://openai.com/pricing

## Security Tips

- **Never commit your API key to Git!**
- Use environment variables or configuration files that are in `.gitignore`
- If you accidentally commit a key, revoke it immediately and create a new one
- Set up usage limits to prevent unexpected charges
- Monitor your usage regularly

## Need Help?

- OpenAI Documentation: https://platform.openai.com/docs
- OpenAI API Reference: https://platform.openai.com/docs/api-reference
- OpenAI Community: https://community.openai.com/

