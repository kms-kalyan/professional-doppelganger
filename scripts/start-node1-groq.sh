#!/bin/bash

# Start Node 1 with Groq (FREE & FAST LLM): Cluster port 2551, HTTP port 8080
# Get your free Groq API key from: https://console.groq.com/
# Groq is much faster than HuggingFace and has a generous free tier!

mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 gsk_YOUR_GROQ_API_KEY llama-3.1-8b-instant groq" \
    -Dakka.remote.artery.canonical.port=2551

