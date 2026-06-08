#!/bin/bash

# Start the Professional Doppelganger node.
#   Cluster port: 2551   HTTP port: 8080
#
# Primary LLM provider:  OpenAI  (default model: gpt-4o-mini)
# Fallback LLM provider: Claude  (default model: claude-haiku-4-5)
#
# The OpenAI key is passed as the 3rd argument below.
# The Claude fallback key is read from the ANTHROPIC_API_KEY environment variable
# (keeps the key out of the process arguments). If it is unset, the node still runs
# with OpenAI only and the fallback is disabled.
#
# Get an OpenAI API key: https://platform.openai.com/api-keys
# Get an Anthropic API key: https://console.anthropic.com/

# Set your Claude fallback key here (or export it in your shell before running):
export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-sk-ant-YOUR_ANTHROPIC_KEY}"

mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 sk-proj-YOUR_OPENAI_KEY gpt-4o-mini claude-haiku-4-5" \
    -Dakka.remote.artery.canonical.port=2551
