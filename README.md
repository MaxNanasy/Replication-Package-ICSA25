Replication Package for "Enabling Architecture Traceability by LLM-based Architecture Component Name Extraction"
by Johnathan Pham, Max Nanasy, Psy

Requirements
The requirements are defined in INSTALL.md.

Minimum requirements:

Java 17 or higher

Maven 3.9+

Docker (optional, for containerized execution)

~50GB free disk space (for datasets and cached responses)

Internet connection (for API calls if using live LLMs)

For live LLM execution (optional):

OpenAI API key (for GPT-4o, GPT-4.1, GPT-4.5)

Ollama installed (for Llama 3.3, Llama 4, Mistral Large 2)

GPU recommended for local LLM execution

Quickstart
If you just want to run the evaluation with cached results (no API calls, no cost):

Docker
bash
docker run -it --rm ghcr.io/ardoco/icsa25
mvn -q test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TraceLinkEvaluationSadSamViaLlmCodeIT
Local (with Maven)
bash
mvn -q test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TraceLinkEvaluationSadSamViaLlmCodeIT
The test takes 25-30 minutes on a MacBook Air M2 using cached responses.

Overview of the Repository
Directory	Description
pipeline-tlr/	Core source code for TransArC and LLM integration
tests/	Test classes for evaluation
cache-llm/	Cached LLM requests/responses in JSON format (for replication)
results/	Evaluation results in human-readable logging format
stages-tlr/	Pipeline stage configurations
Key Classes
Class	Purpose
TraceLinkEvaluationSadSamViaLlmCodeIT	Main test class for TLR evaluation using TransArC
LLMArchitectureProviderInformant	Core logic for extracting component names via LLMs
LargeLanguageModel	Enum defining supported LLMs (GPT-4o, GPT-4.1, Llama 3.3, Llama 4, etc.)
LLMStatisticsCollector	NEW Collects statistics across multiple runs
ArDoCoForSadSamViaLlmCodeTraceabilityLinkRecovery	Pipeline executor with live LLM support
Environment Variables
Variable	Purpose	Default
OPENAI_API_KEY	OpenAI API key for GPT models	sk-DUMMY (cached mode)
OPENAI_ORG_ID	OpenAI organization ID (optional)	empty
OLLAMA_HOST	Host for Ollama local LLMs	http://localhost:11434
OLLAMA_USER	Ollama authorization user (optional)	not set
OLLAMA_PASSWORD	Ollama authorization password (optional)	not set
To set environment variables (macOS/Linux):

bash
export OPENAI_API_KEY="your-actual-key"
export OLLAMA_HOST="http://localhost:11434"
To use cached responses (no API calls):

bash
export OPENAI_API_KEY="sk-DUMMY"
Running with Live LLMs (NEW)
By default, the replication uses cached responses. To use live LLM API calls (which incur costs), follow these steps:

Step 1: Set your API keys
bash
# For OpenAI models (GPT-4o, GPT-4.1, GPT-4.5)
export OPENAI_API_KEY="sk-proj-your-actual-key"

# For local Ollama models (Llama 3.3, Llama 4, Mistral)
brew install ollama
ollama pull llama3.3:70b
ollama serve
Step 2: Remove the cache directory
bash
rm -rf cache-llm
Step 3: Run with live LLM flag
bash
mvn test -Dtest=TraceLinkEvaluationSadSamViaLlmCodeIT -Dlive=true
Step 4: Run multiple iterations for statistics
bash
# Run 5 times for statistical significance
mvn test -Dtest=TraceLinkEvaluationSadSamViaLlmCodeIT -Druns=5
Results Format
Each evaluation produces output like:

text
Evaluating project MEDIASTORE with LLM 'GPT_4_O_MINI'
Prompts: DOCUMENTATION_ONLY_V1, null, null

MEDIASTORE (SadSamViaLlmCodeTraceabilityLinkRecoveryEvaluation):
    Precision:    0.49
    Recall:       0.52
    F1:           0.50
    Accuracy:     0.99
    Specificity:  0.99
    Phi Coef.:    0.50
NEW: Statistics Summary (with multiple runs)
When running multiple iterations, you will see:

text
========== LLM STATISTICS SUMMARY ==========
--- MEDIASTORE:GPT-4o (5 runs) ---
F1:        mean=0.8623, std=0.0234, min=0.8234, max=0.8912
95% CI:    [0.8478, 0.8768]
✅ Variation: LOW - Results are stable
=============================================
Future Extension Scenarios
Adding New LLMs
To add a new LLM, edit LargeLanguageModel.java:

java
public enum LargeLanguageModel {
    // Existing models
    GPT_4_O("gpt-4o", "OpenAI GPT-4o", "https://api.openai.com/v1"),
    GPT_4_O_MINI("gpt-4o-mini", "OpenAI GPT-4o Mini", "https://api.openai.com/v1"),
    
    // ADD NEW MODELS HERE:
    GPT_4_1("gpt-4.1", "OpenAI GPT-4.1", "https://api.openai.com/v1"),
    GPT_4_5("gpt-4.5", "OpenAI GPT-4.5", "https://api.openai.com/v1"),
    LLAMA_3_3_70B("llama3.3:70b", "Meta Llama 3.3 70B", "http://localhost:11434"),
    LLAMA_4_70B("llama4:70b", "Meta Llama 4 70B", "http://localhost:11434"),
    MISTRAL_LARGE_2("mistral-large:2407", "Mistral Large 2", "http://localhost:11434"),
    DEEPSEEK_V3("deepseek-v3:671b", "DeepSeek V3", "http://localhost:11434"),
    ;
    
    private final String modelId;
    private final String displayName;
    private final String apiEndpoint;
    
    // constructor, getters...
}
Adding New Projects
To add a new project, edit CodeProject.java:

java
public enum CodeProject {
    // Existing projects
    MEDIASTORE,
    TEASTORE,
    TEAMMATES,
    BIGBLUEBUTTON,
    JABREF,
    
    // ADD NEW PROJECTS HERE:
    YOUR_NEW_PROJECT,
    ;
    
    // Add project configuration (text file, model file, code directory)
}
Running Custom Pipeline
To run the ArDoCo pipeline programmatically:

java
ArDoCoForSadSamViaLlmCodeTraceabilityLinkRecovery runner = 
    new ArDoCoForSadSamViaLlmCodeTraceabilityLinkRecovery("MyProject");

runner.setUp(
    inputTextFile,           // SAD text file
    inputCodeDirectory,      // Source code directory
    additionalConfigs,       // Map of configs (e.g., {"useLiveLLM": "true"})
    outputDirectory,         // Output directory
    LargeLanguageModel.GPT_4_1,  // LLM to use
    documentationPrompt,     // Prompt for doc extraction (or null)
    codeExtractionPrompt,    // Prompt for code extraction (or null)
    codeFeatures,            // Code features (or null)
    aggregationPrompt        // Aggregation prompt (or null)
);

runner.run();
Non-determinism in LLM Results
LLMs are inherently non-deterministic. Even with temperature=0, you may see variations due to:

API-side model updates

Floating-point arithmetic differences across hardware

Network latency and load balancing

Mitigation Strategies
Run multiple iterations (recommended: 5-10 runs)

Report statistics (mean, standard deviation, confidence intervals)

Use the same API version (pin to specific model versions)

Set USE_LIVE_LLM = false to use cached responses for exact replication

Expected Variation Range
Metric	Typical Variation
Precision	±0.02-0.05
Recall	±0.01-0.03
F1	±0.02-0.04
Troubleshooting
Error	Solution
mvn: command not found	Install Maven: brew install maven (macOS) or apt-get install maven (Linux)
Java not found	Install Java 17: brew install openjdk@17
OPENAI_API_KEY not set	Set environment variable or use sk-DUMMY for cached mode
Cache directory not found	Normal for first run; cache will be created
High variation in results	Increase number of runs: -Druns=10
License
MIT License

Citation
If you use this replication package, please cite:

bibtex
@inproceedings{pham2025replication,
  title={Replication Package for "Enabling Architecture Traceability by LLM-based Architecture Component Name Extraction"},
  author={Pham, Johnathan and Nanasy, Max and Psy},
  booktitle={ICSA 2025 Replication Package},
  year={2025}
}
About
This replication package is forked from the original ICSA25 replication package and extended with:

Live LLM support (GPT-4.1, Llama 3.3, Llama 4, Mistral Large 2)

Multiple run statistics (mean, std dev, confidence intervals)

Nondeterminism handling (variation analysis)

Cost tracking for API calls

Enhanced prompt strategies

Original DOI: 10.5281/zenodo.14506935

How to Save This README
bash
cd ~/Downloads/Replication-Package-ICSA25
nano README.md
Then paste the content above, save (Ctrl+O, then Enter, then Ctrl+X).

Then commit:

bash
git add README.md
git commit -m "Update README with live LLM support, statistics, and nondeterminism handling"
git push origin main
