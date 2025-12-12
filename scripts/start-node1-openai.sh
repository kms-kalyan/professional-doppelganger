#!/bin/bash

# Start Node 1: Cluster port 2551, HTTP port 8080
# Uses OpenAI API
# Get your API key from: https://platform.openai.com/api-keys
# Popular models: gpt-3.5-turbo, gpt-4, gpt-4-turbo

mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 sk_YOUR_OPENAI_API_KEY gpt-3.5-turbo openai" \
    -Dakka.remote.artery.canonical.port=2551