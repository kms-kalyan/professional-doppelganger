# LLM Akka Cluster

A distributed Akka Cluster application that integrates with OpenAI API to process user queries through a web interface.

## Features

- **Akka Cluster (Typed)**: Distributed system with 2+ nodes
- **Multiple Actors per Node**: RoutingActor, LLMActor, LoggingActor
- **Actor Communication Patterns**:
  - **Tell**: Fire-and-forget messages (logging)
  - **Ask**: Request-response pattern (LLM queries)
  - **Forward**: Message forwarding with original sender context
- **OpenAI Integration**: Processes queries using OpenAI models
- **Web Interface**: Modern HTML/JavaScript frontend

## Architecture

### Message Flow

1. User submits query via web interface
2. HTTP Server receives request and uses **ask** pattern to send QueryMessage to RoutingActor
3. RoutingActor:
   - Uses **tell** pattern to send log message to LoggingActor (fire-and-forget)
   - Uses message adapter to request response from LLMActor
   - LLMActor processes query via OpenAI API and responds
   - RoutingActor uses **tell** to forward log message to LoggingActor (demonstrates forward pattern)
   - RoutingActor sends QueryResponse back to HTTP Server
4. HTTP Server returns JSON response to web interface

### Actors

- **RoutingActor**: Routes messages between actors, demonstrates tell, ask, and forward patterns
- **LLMActor**: Handles communication with OpenAI API
- **LoggingActor**: Logs messages, demonstrates forward pattern (receives messages with original sender)
- **HttpServerActor**: Handles HTTP requests and serves web interface

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- OpenAI API key (get from https://platform.openai.com/api-keys)

## Setup

1. **Clone the repository** (if applicable)

2. **Get and configure OpenAI API key**:
   - **Get API key**: See detailed instructions in `OPENAI_API_KEY_SETUP.md`
   - Quick steps:
     1. Go to https://platform.openai.com/ and create an account
     2. Go to https://platform.openai.com/api-keys
     3. Click "Create new secret key" → Name it → Generate
     4. Copy the token (starts with `sk-`)
   - Edit `scripts/start-node1.sh` and `scripts/start-node2.sh`
   - Replace `sk_YOUR_API_KEY` with your actual token

3. **Make scripts executable**:
   ```bash
   chmod +x scripts/start-node1.sh
   chmod +x scripts/start-node2.sh
   ```

## Running the Application

### Option 1: Using Startup Scripts

**Terminal 1 - Start Node 1:**
```bash
./scripts/start-node1.sh
```
This starts:
- Cluster node on port 2551
- HTTP server on port 8080

**Terminal 2 - Start Node 2:**
```bash
./scripts/start-node2.sh
```
This starts:
- Cluster node on port 2552
- HTTP server on port 8081

### Option 2: Manual Start

**Node 1:**
```bash
mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 sk_YOUR_API_KEY gpt-3.5-turbo openai"
```

**Node 2:**
```bash
mvn exec:java -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2552 8081 sk_YOUR_API_KEY gpt-3.5-turbo openai"
```

## Using the Web Interface

1. Open your browser and navigate to:
   - Node 1: http://localhost:8080
   - Node 2: http://localhost:8081

2. Type your query in the input field and click "Send"

3. The system will:
   - Route your query through the cluster
   - Process it via OpenAI
   - Return the response

## API Endpoint

You can also use the API directly:

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What is Akka?"}'
```

Response:
```json
{
  "query": "What is Akka?",
  "response": "...",
  "sessionId": "...",
  "success": true
}
```

## Project Structure

```
llm-akka-cluster/
├── src/main/java/com/doppelganger/llm/
│   ├── actors/
│   │   ├── HttpServerActor.java    # HTTP server and routing
│   │   ├── LLMActor.java           # OpenAI integration
│   │   ├── LoggingActor.java       # Logging service
│   │   └── RoutingActor.java       # Message routing
│   ├── messages/
│   │   ├── LLMRequest.java         # LLM request message
│   │   ├── LLMResponse.java        # LLM response message
│   │   ├── LogMessage.java         # Log message
│   │   ├── QueryMessage.java       # User query message
│   │   └── QueryResponse.java      # Query response message
│   └── ClusterApp.java             # Main application
├── src/main/resources/
│   ├── application.conf            # Akka configuration
│   └── logback.xml                 # Logging configuration
├── src/main/webapp/
│   └── index.html                  # Web interface
├── scripts/
│   ├── start-node1.sh              # Node 1 startup script
│   └── start-node2.sh              # Node 2 startup script
└── pom.xml                         # Maven configuration
```

## Requirements Demonstrated

This project fully implements all required features:

✅ **Akka Cluster**: 2+ nodes running locally (Typed API)  
✅ **Service Actors**: 5 actors per node (RoutingActor, LLMActor, LoggingActor, MemoryActor, HttpServerActor)  
✅ **Tell Pattern**: Fire-and-forget messages demonstrated multiple times (RoutingActor → LoggingActor, RoutingActor → MemoryActor)  
✅ **Ask Pattern**: Request-response pattern demonstrated multiple times (HttpServerActor → RoutingActor, RoutingActor → LLMActor, RoutingActor → MemoryActor)  
✅ **Forward Pattern**: Messages forwarded to LoggingActor with original sender context preserved  
✅ **LLM Integration**: OpenAI API integration via LLMActor (Groq also supported)  
✅ **Web Interface**: HTML/JavaScript frontend  
✅ **Message Flow**: Complete flow from user query to LLM response

**For detailed requirements compliance documentation, see [REQUIREMENTS_COMPLIANCE.md](REQUIREMENTS_COMPLIANCE.md)**

## Configuration

Cluster configuration is in `src/main/resources/application.conf`:
- Seed nodes: 127.0.0.1:2551 and 127.0.0.1:2552
- Cluster port is set dynamically via system property

## Troubleshooting

- **Cluster not forming**: Ensure both nodes are started and can communicate
- **API errors**: Verify your OpenAI API key is correct and has credits/balance
- **Port conflicts**: Change ports in startup scripts if 8080/8081 or 2551/2552 are in use

## License

This is a project for educational purposes demonstrating Akka Cluster patterns.

