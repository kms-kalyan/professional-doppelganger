# Professional Doppelganger Chatbot Setup

This chatbot acts as your professional doppelganger, answering questions on your behalf using your professional details.

## Overview

The chatbot loads your professional details from a JSON file and uses them as context to answer questions. It will respond as if it is you, using only the information you provide.

## Setup Instructions

### Step 1: Create Your Professional Details JSON File

1. Open `src/main/resources/data/professional_details.json`
2. Replace the template with your actual professional information

### Step 2: Fill in Your Details

Edit the JSON file with your information:

```json
{
  "name": "John Doe",
  "title": "Senior Software Engineer",
  "email": "john.doe@example.com",
  "summary": "Experienced software engineer with 10+ years in full-stack development...",
  "skills": [
    "Java",
    "Python",
    "Akka",
    "Distributed Systems"
  ],
  "experience": [
    {
      "company": "Tech Corp",
      "position": "Senior Software Engineer",
      "duration": "2020 - Present",
      "description": "Led development of distributed systems using Akka..."
    }
  ],
  "education": [
    {
      "institution": "University Name",
      "degree": "BS Computer Science",
      "year": "2010"
    }
  ],
  "projects": [
    {
      "name": "Akka Cluster System",
      "description": "Built distributed system using Akka Cluster",
      "technologies": ["Java", "Akka", "Scala"]
    }
  ],
  "certifications": [
    "AWS Certified Solutions Architect",
    "Oracle Certified Java Developer"
  ],
  "languages": [
    "English",
    "Spanish"
  ],
  "interests": [
    "Distributed Systems",
    "Machine Learning"
  ]
}
```

### Step 3: Start the Application

```bash
./scripts/start-node1.sh
```

The application will automatically load your professional details at startup.

## How It Works

1. **At Startup**: Your professional details JSON is loaded and formatted
2. **When Asked a Question**: The chatbot receives:
   - Your professional profile (as context)
   - Instructions to act as your doppelganger
   - The user's question
3. **Response**: The LLM answers based on your professional details

## Example Interactions

**User asks:** "What are your skills?"

**Chatbot responds:** "Based on my profile, my skills include Java, Python, Akka, and Distributed Systems..."

**User asks:** "Tell me about your experience at Tech Corp"

**Chatbot responds:** "I worked as a Senior Software Engineer at Tech Corp from 2020 to Present. I led development of distributed systems using Akka..."

**User asks:** "What's your favorite programming language?"

**Chatbot responds:** (Uses information from your profile, or says it's not specified if not in the profile)

## Custom File Location

To use a different JSON file:

```bash
mvn exec:java -Dprofessional.details.path="/path/to/your/details.json" \
    -Dexec.mainClass="com.doppelganger.llm.ClusterApp" \
    -Dexec.args="2551 8080 hf_YOUR_API_KEY google/flan-t5-large"
```

## JSON Schema

All fields are optional. Include only what's relevant:

- **name**: Your full name
- **title**: Current job title
- **email**: Professional email
- **summary**: Brief professional summary
- **skills**: Array of skill strings
- **experience**: Array of work experience objects
  - company, position, duration, description
- **education**: Array of education objects
  - institution, degree, year
- **projects**: Array of project objects
  - name, description, technologies
- **certifications**: Array of certification strings
- **languages**: Array of language strings
- **interests**: Array of interest strings

## Tips

1. **Be Comprehensive**: Include all relevant professional information
2. **Keep It Current**: Update the JSON file regularly
3. **Be Specific**: Detailed descriptions help the chatbot answer better
4. **Test Questions**: Try various questions to see how well it represents you

## Troubleshooting

### Professional Details Not Loading

Check the logs for:
- File path being searched
- JSON parsing errors
- File not found messages

### Chatbot Not Using Your Details

- Verify the JSON file is valid JSON
- Check that professional details were loaded (see startup logs)
- Ensure the file is in `src/main/resources/data/` and rebuilt

### Rebuild After Changes

After editing the JSON file:
```bash
mvn clean compile
```

Then restart the application.

## Example Use Cases

- **Job Interviews**: Practice answering questions about yourself
- **Networking**: Let others learn about you through conversation
- **Professional Profile**: Automated Q&A about your background
- **Resume Helper**: Answer questions based on your resume data

The chatbot will only use information from your JSON file, so make sure it's complete and accurate!

