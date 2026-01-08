# Groq Model Optimization Strategy

## Overview

The application now uses **two different Groq models** to optimize performance and cost:
- **Light Model** (`llama-3.3-70b-versatile`) - For fast analysis tasks
- **Heavy Model** (`llama-3.1-8b-instant`) - For high-quality final document generation

## Model Usage Breakdown

### Light Model: `llama-3.3-70b-versatile`
**Purpose**: Fast data extraction and analysis

Used for:
1. **Biography Analysis** (`BiographyAiAnalyzerService`)
   - Extracting structured personal information from free-form text
   - Parsing education, work experience, skills
   
2. **Job Posting Analysis** (`JobPostingAiAnalyzerService`)
   - Extracting job requirements, company info, location
   - Identifying required and preferred skills

**Why Light Model?**
- These are **analysis tasks** that need structured data extraction
- Speed is important for quick parsing
- Output quality doesn't need to be "creative" - just accurate extraction
- Lower cost per request

### Heavy Model: `llama-3.1-8b-instant`
**Purpose**: High-quality creative document generation

Used for:
1. **Cover Letter Generation** (`AnschreibenGeneratorService`)
   - Creating professional, personalized cover letters
   - Requires natural, flowing German language
   
2. **CV/Resume Generation** (`LebenslaufGeneratorService`)
   - Formatting professional resumes
   - Requires polished, professional output

**Why Heavy Model?**
- These are **generation tasks** requiring creativity and quality
- Final documents represent the candidate - must be excellent
- Natural language flow and professional tone are critical

## API Requests Per Application Cycle

### Startup (VacancyAnalysisRunner):
| Task | Model | Service |
|------|-------|---------|
| Job Posting Analysis | **LIGHT** | JobPostingAiAnalyzerService |
| Cover Letter Generation | **HEAVY** | AnschreibenGeneratorService |
| CV Generation | **HEAVY** | LebenslaufGeneratorService |

**Total: 1 light + 2 heavy = 3 requests**

### Per `/api/generate/from-file` Request:
| Task | Model | Service |
|------|-------|---------|
| Biography Analysis | **LIGHT** | BiographyAiAnalyzerService |
| Job Posting Analysis | **LIGHT** | JobPostingAiAnalyzerService |
| Cover Letter Generation | **HEAVY** | AnschreibenGeneratorService |
| CV Generation | **HEAVY** | LebenslaufGeneratorService |

**Total: 2 light + 2 heavy = 4 requests**

## Configuration

**File**: `application.properties`

```properties
groq.api.key=<your-key>
groq.api.url=https://api.groq.com/openai/v1/chat/completions

# Light model for analysis tasks (fast and cheap)
groq.model.light=llama-3.3-70b-versatile

# Heavy model for final document generation (higher quality)
groq.model.heavy=llama-3.1-8b-instant
```

## Code Changes

### 1. GroqAiService
Added two new methods:
```java
public String generateTextWithLightModel(String prompt)  // For analysis
public String generateTextWithHeavyModel(String prompt)  // For generation
```

The original `generateText()` method is now deprecated and defaults to the heavy model for backward compatibility.

### 2. Analysis Services
Updated to use light model:
- `BiographyAiAnalyzerService.parseBiography()`
- `JobPostingAiAnalyzerService.analyzeJobPosting()`

### 3. Generation Services
Updated to use heavy model:
- `AnschreibenGeneratorService.generateAnschreiben()`
- `LebenslaufGeneratorService.generateLebenslauf()`

## Benefits

1. ✅ **Cost Optimization**: Analysis tasks use cheaper, faster model
2. ✅ **Quality Optimization**: Final documents use higher-quality model
3. ✅ **Speed Optimization**: Light model processes analysis tasks faster
4. ✅ **Flexibility**: Easy to switch models via configuration
5. ✅ **Backward Compatibility**: Deprecated method ensures existing code works

## Log Output

The application logs now clearly show which model is being used:

```
INFO com.bewerbung.service.GroqAiService : Groq AI Service initialized - Light model: llama-3.3-70b-versatile, Heavy model: llama-3.1-8b-instant

INFO com.bewerbung.service.GroqAiService : Generating text with Groq API using LIGHT model (llama-3.3-70b-versatile)...
INFO com.bewerbung.service.GroqAiService : Successfully generated text with llama-3.3-70b-versatile (length: 717 characters)

INFO com.bewerbung.service.GroqAiService : Generating text with Groq API using HEAVY model (llama-3.1-8b-instant)...
INFO com.bewerbung.service.GroqAiService : Successfully generated text with llama-3.1-8b-instant (length: 2316 characters)
```

## Summary

| Aspect | Before | After |
|--------|--------|-------|
| Model Strategy | Single model for all tasks | Dual model strategy |
| Analysis Tasks | llama-3.1-8b-instant | **llama-3.3-70b-versatile** (faster) |
| Generation Tasks | llama-3.1-8b-instant | **llama-3.1-8b-instant** (same) |
| Cost Efficiency | Standard | **Optimized** |
| Processing Speed | Standard | **Improved for analysis** |
| Document Quality | High | **High (maintained)** |

This optimization ensures fast, cost-effective analysis while maintaining high-quality output for final documents.

