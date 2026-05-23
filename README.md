# Professional Doppelganger

> A distributed multi-LLM chatbot that answers questions about me in my own voice — built on Akka Cluster with an actor-based architecture.

Most professional profiles are static. Resumes and LinkedIn pages can't answer follow-up questions, can't hold context, and can't scale. Professional Doppelganger is an experiment in fixing that: a digital twin you can actually talk to, built as a distributed actor system rather than a single-process chatbot.

**Stack:** Java 17 · Akka Typed · OpenAI API · Claude API · Maven

---

## What makes it interesting

- **Actor-based, not request-based.** The conversation isn't handled by one server — it's a workflow across five typed actors (routing, LLM, memory, logging, HTTP), communicating with explicit `ASK` / `TELL` / `FORWARD` semantics.
- **Multi-provider by design.** Two cluster nodes run two different LLM providers (OpenAI on one, Claude on the other). Lets me trade off latency, cost, and quality without changing client code.
- **Conversation memory as a first-class actor.** The `MemoryActor` owns history; the `LLMActor` is stateless. This made the hardest part of the project — keeping responses grounded in prior turns without prompt bloat — a tractable design problem instead of a tangle of session globals.

**Two nodes, independent processing:** Node 1 (port 8080) routes to Claude; Node 2 (port 8081) routes to OpenAI. They form a cluster but don't currently share workload — each handles its own requests end-to-end. Cross-node routing is on the roadmap.

## The hardest part

Keeping responses *on topic* across a multi-turn conversation, without letting the prompt grow until it dominated both cost and coherence.

Naive solutions failed quickly:

- Stuffing the full history into every prompt → token bloat, slower responses, model started drifting.
- Truncating to the last N turns → lost important context from earlier in the conversation.
- Letting the LLM "summarize itself" each turn → introduced its own hallucinations into the memory.

The working approach: the `MemoryActor` keeps a structured rolling window plus a separate slot for "anchored facts" (name, role, key experience) that always get injected. The `RoutingActor` shapes the prompt before handing off to the `LLMActor`, so the LLM only ever sees a curated context, not the raw history. Responses stayed grounded and the prompt size stayed bounded.

## Communication patterns

| Pattern | Used for | Why |
|---|---|---|
| `ASK` | HTTP request → routing → LLM → response | Blocking the response on the LLM is the critical path |
| `TELL` | Memory updates, log writes | Side effects shouldn't block the user |
| `FORWARD` | Preserving original sender through the routing chain | The HTTP actor needs the reply, not the router |

## Running it locally

```bash
# Clone and configure
git clone https://github.com/kms-kalyan/professional-doppelganger.git
cd professional-doppelganger
cp .env.example .env  # add your OpenAI and Claude API keys

# Start both nodes
./scripts/run-node1.sh   # Claude, port 8080
./scripts/run-node2.sh   # OpenAI, port 8081

# Talk to it
open http://localhost:8080
```

Try queries like "What kind of roles are you looking for?" or "Walk me through your AWS experience." The two ports route to different providers — useful for comparing model behavior side by side.

## What's next

- **Cross-node routing** — currently each node processes independently; the next step is load-balancing requests across the cluster.
- **RAG grounding** — replace the static prompt context with retrieval from a vector store over my actual project writeups and resume.
- **Persistent memory** — move conversation state out of the actor's in-memory store into Postgres or Redis.
- **Tool use** — let the LLM actor call APIs (fetch GitHub stats, look up a project) rather than only generating text.

## Tech stack

- **Language:** Java 17
- **Concurrency:** Akka Typed Actors, Akka Cluster
- **LLMs:** OpenAI (GPT-4 / GPT-3.5), Claude (LLaMA 3, Mixtral)
- **HTTP:** Akka HTTP
- **Build:** Maven

---

*Built solo as a learning project to explore actor-based architectures applied to LLM workflows.*
