#!/bin/bash

# Start Node 1: Cluster port 2551, HTTP port 8080
# Uses Groq by default (FREE & FAST - Recommended!)
# Get your free Groq API key from: https://console.groq.com/
# Alternative: Use HuggingFace by adding "huggingface" as 5th argument

mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 gsk_YOUR_GROQ_API_KEY llama-3.1-8b-instant groq" \
    -Dakka.remote.artery.canonical.port=2551

