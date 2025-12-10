#!/bin/bash

# Start Node 1: Cluster port 2551, HTTP port 8080
# Uses HuggingFace Inference API (FREE)
# Get your free API key from: https://huggingface.co/settings/tokens
# You can also use "none" as API key for public models (rate limited)
# Popular free models: google/flan-t5-large, microsoft/DialoGPT-medium, facebook/blenderbot-400M-distill

mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 hf_YOUR_HUGGINGFACE_API_KEY google/flan-t5-large huggingface" \
    -Dakka.remote.artery.canonical.port=2551