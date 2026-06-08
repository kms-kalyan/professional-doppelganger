# Professional Doppelganger

> A distributed multi-LLM chatbot that answers questions about me in my own voice — built on Akka Cluster with an actor-based architecture.

Most professional profiles are static. Resumes and LinkedIn pages can't answer follow-up questions, can't hold context, and can't scale. Professional Doppelganger is an experiment in fixing that: a digital twin you can actually talk to, built as a distributed actor system rather than a single-process chatbot.

**Stack:** Java 17 · Akka Typed · OpenAI API · Claude API · Docker · Maven

**🔗 Live demo:** https://professional-doppelganger.onrender.com  *(free tier — the first load may take ~30s to wake up)*

---

## What makes it interesting

- **Actor-based, not request-based.** The conversation isn't handled by one server — it's a workflow across five typed actors (routing, LLM, memory, logging, HTTP), communicating with explicit `ASK` / `TELL` / `FORWARD` semantics.
- **Multi-provider with automatic failover.** OpenAI is the primary model; a `FallbackLLMActor` transparently retries on Claude if the primary errors or hits a rate limit. A provider outage degrades gracefully instead of taking the bot down — and the client code never changes.
- **Conversation memory as a first-class actor.** The `MemoryActor` owns history; the LLM actors are stateless per request. This made the hardest part of the project — keeping responses grounded in prior turns without prompt bloat — a tractable design problem instead of a tangle of session globals.

**Single node, automatic failover:** The app runs as one Akka Cluster node serving HTTP on port 8080. Inside it, each request goes to OpenAI first and falls back to Claude on failure. The cluster setup keeps it horizontally extensible — multi-node load-balancing is on the roadmap.

## The hardest part

Keeping responses *on topic* across a multi-turn conversation, without letting the prompt grow until it dominated both cost and coherence.

Naive solutions failed quickly:

- Stuffing the full history into every prompt → token bloat, slower responses, model started drifting.
- Truncating to the last N turns → lost important context from earlier in the conversation.
- Letting the LLM "summarize itself" each turn → introduced its own hallucinations into the memory.

The working approach has two parts. The `MemoryActor` keeps a **bounded rolling window** — the last 5 exchanges per session, no more — so the conversation context can't grow without limit. Separately, the identity grounding lives in a **profile-derived system prompt**, built from a structured JSON profile and always injected, carrying explicit rules ("only answer from verified experience; flag anything outside it"). The `RoutingActor` pulls the bounded history from memory, and the LLM actor composes the final prompt from those two pieces — so the model always sees curated context (recent turns + grounded identity), never the raw unbounded history. Responses stayed grounded and prompt size stayed bounded.

## Communication patterns

| Pattern | Used for | Why |
|---|---|---|
| `ASK` | HTTP request → routing → LLM → response | Blocking the response on the LLM is the critical path |
| `TELL` | Memory updates, log writes | Side effects shouldn't block the user |
| `FORWARD` | Preserving original sender through the routing chain | The HTTP actor needs the reply, not the router |

## Running it locally

```bash
git clone https://github.com/kms-kalyan/professional-doppelganger.git
cd professional-doppelganger

# The app reads keys from the environment
export OPENAI_API_KEY=sk-...         # primary (required)
export ANTHROPIC_API_KEY=sk-ant-...  # Claude fallback (optional)

# Build an executable jar and run it (serves http://localhost:8080)
mvn clean package
java -jar target/app.jar
```

Then open http://localhost:8080 and try queries like "What kind of roles are you looking for?" or "Walk me through your AWS experience." Optional model overrides: `OPENAI_MODEL` (default `gpt-4o-mini`) and `CLAUDE_MODEL` (default `claude-haiku-4-5`).

### Run with Docker

```bash
docker build -t professional-doppelganger .
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=sk-... \
  -e ANTHROPIC_API_KEY=sk-ant-... \
  professional-doppelganger
```

The included `render.yaml` deploys it to Render's free tier — point Render at the repo (New → Blueprint) and it builds the Dockerfile and prompts for the two keys.

## What's next

- **Cross-node routing** — currently a single node; the next step is scaling to a multi-node cluster that load-balances requests.
- **RAG grounding** — replace the static prompt context with retrieval from a vector store over my actual project writeups and resume.
- **Persistent memory** — move conversation state out of the actor's in-memory store into Postgres or Redis.
- **Tool use** — let the LLM actor call APIs (fetch GitHub stats, look up a project) rather than only generating text.

## Tech stack

- **Language:** Java 17
- **Concurrency:** Akka Typed Actors, Akka Cluster
- **LLMs:** OpenAI (`gpt-4o-mini`, primary) + Claude (`claude-haiku-4-5`, automatic fallback)
- **HTTP:** Akka HTTP
- **Build & deploy:** Maven, Docker, Render

---

*Built solo as a learning project to explore actor-based architectures applied to LLM workflows.*
