#!/bin/bash

# Start Node 2: Cluster port 2552, HTTP port 8081
# Uses OpenAI API
# Get your API key from: https://platform.openai.com/api-keys
# Popular models: gpt-3.5-turbo, gpt-4, gpt-4-turbo

mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2552 8081 sk_YOUR_API_KEY gpt-3.5-turbo openai" \
    -Dakka.remote.artery.canonical.port=2552

