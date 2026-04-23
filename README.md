# Professional Doppelganger

**A Distributed AI System for Personal Digital Representation**

## Problem Statement

Traditional professional profiles (resumes, LinkedIn) are static and fail to capture how a person actually communicates, thinks, and responds in real-world scenarios.

This creates a gap:

- Recruiters and collaborators cannot interact dynamically with a candidate’s knowledge and experience
- Candidates cannot scale their professional presence or respond to repeated queries efficiently

Who is affected?

- Job seekers
- Recruiters and hiring managers
- Professionals engaging in networking or knowledge sharing

Why does this matter?

If solved, professionals could have an always-available AI representative that:

- Answers questions in their voice
- Maintains context across conversations
- Scales communication without losing personalization

Success looks like:

- Users can interact with a digital persona and receive consistent, context-aware responses
- Conversations feel natural, personalized, and aligned with the individual

## Solution Overview

Professional Doppelganger is a distributed AI-powered system that acts as a digital twin of a professional.

It:

- Accepts user queries via a web interface
- Understands context using conversation memory
- Generates personalized responses using LLMs
- Maintains consistent tone and communication style

Unlike a simple chatbot, this system is designed as a modular, orchestrated workflow of AI components, making it extensible toward agentic systems.

Key Features:

- Context-aware conversation (memory persistence)
- Multi-LLM support (OpenAI + Groq)
- Distributed architecture using Akka Cluster
- Modular actor-based design
- Real-time response generation

Role of AI:

AI is core to the system, not supplementary. Without LLMs, the system would reduce to a static FAQ engine. AI enables:

- Natural language understanding
- Personalized response generation
- Adaptive communication

## AI Integration

Models & Providers:

- OpenAI (GPT-3.5 / GPT-4 / GPT-4-turbo)
- Groq (LLaMA 3, Mixtral)

Why multiple providers?

- Tradeoff between latency, cost, and performance
- Flexibility to switch models dynamically

Agentic Patterns Used:

- Orchestration via RoutingActor (central decision layer)
- Multi-step workflow (memory → reasoning → response)
- Tool abstraction (LLMActor as a unified interface to external AI services)

What worked well:

- Modular separation of concerns made AI integration clean
- Multi-provider setup improved flexibility and resilience

Limitations:

- No retrieval-based grounding (no RAG yet)
- Limited tool usage (no external API actions yet)
- Responses depend on prompt + memory only

## Architecture / Design Decisions

The system is built using an Akka Typed Actor model deployed as a distributed cluster.

### Core Components

- RoutingActor → Orchestrates the workflow
- LLMActor → Handles LLM API communication
- MemoryActor → Stores conversation history
- LoggingActor → Handles observability
- HttpServerActor → Exposes REST API

### Distributed Setup

- 2 nodes running locally:
  - Node 1 → Groq LLM
  - Node 2 → OpenAI LLM
- Nodes form a cluster but process requests independently

### Communication Patterns

- ASK → synchronous request-response (critical path)
- TELL → async side effects (logging, memory updates)
- FORWARD → preserves original request context

### Design Tradeoffs

- ✅ Actor model → high modularity and scalability
- ✅ Multi-node setup → simulates distributed systems
- ❌ Nodes do not share workload (no cross-node routing yet)
- ❌ No persistent storage (memory is session-based)

##  AI-Assisted Development

AI tools (ChatGPT, Copilot) were used to:

- Rapidly prototype actor communication patterns
- Debug concurrency and async flows
- Generate boilerplate and refine API integrations

What AI accelerated:

- Faster iteration on architecture
- Reduced time spent on low-level implementation

Limitations:

- Required manual correction for concurrency edge cases
- Needed deeper understanding to validate generated logic

Impact:

AI acted as a force multiplier, allowing focus on system design rather than syntax.

## Getting Started / Setup Instructions

Clone and enter the repo:

```bash
git clone https://github.com/kms-kalyan/professional-doppelganger.git
cd professional-doppelganger
```

Configure Environment Variables:

```bash
cp .env.example .env
```

Update `.env` with:

- OpenAI API Key
- Groq API Key

Run the Application:

Start both nodes:

```bash
# Node 1 (Groq)
./run-node1.sh

# Node 2 (OpenAI)
./run-node2.sh
```

Or run manually with ports:

- Node 1 → 8080
- Node 2 → 8081

## Demo

How to Use:

Open browser at:

- `http://localhost:8080`
- `http://localhost:8081`

Enter queries like:

- “Introduce yourself”
- “What are your strengths?”
- “What roles are you looking for?”

Observe:

- Context-aware responses
- Consistent tone
- Real-time LLM generation

(UI screenshot shown in project slides)

## Testing / Error Handling

Tested for:

- Multi-turn conversations
- API failures and timeouts
- Missing or invalid inputs

Error Handling:

- Graceful fallback on LLM API failure
- Logging of all requests/responses
- Timeout handling for async calls

Edge Cases Considered:

- Empty queries
- Long conversation history
- API latency issues

## Future Improvements / Stretch Goals

- Add RAG with vector database for deeper knowledge grounding
- Enable tool usage (API integrations) for real-world actions
- Implement cross-node communication for load balancing
- Add persistent storage for long-term memory
- Build integrations with:
  - Slack
  - CRM systems
  - Ticketing platforms

## Links

GitHub Repo:

- `https://github.com/kms-kalyan/professional-doppelganger.git`

## Acknowledgments

- OpenAI API
- Groq API
- Akka Actor Framework
- AI coding tools (ChatGPT, Copilot)

## Submission Notes

This project was built as an original work for the Klaviyo AI Builder Residency application and complies with all submission guidelines outlined in the README template.