#!/bin/bash

# Start Node 2: Cluster port 2552, HTTP port 8081
# Uses HuggingFace Inference API (FREE)
# Get your free API key from: https://huggingface.co/settings/tokens
# You can also use "none" as API key for public models (rate limited)

mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2552 8081 hf_YOUR_HUGGINGFACE_API_KEY google/flan-t5-large huggingface" \
    -Dakka.remote.artery.canonical.port=2552

