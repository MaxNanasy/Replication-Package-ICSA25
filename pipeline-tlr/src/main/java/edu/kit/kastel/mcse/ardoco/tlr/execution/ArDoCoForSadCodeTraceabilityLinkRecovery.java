/* Licensed under MIT 2026-2027. */
package edu.kit.kastel.mcse.ardoco.tlr.execution;

import java.io.File;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.core.common.util.CommonUtilities;
import edu.kit.kastel.mcse.ardoco.core.common.util.DataRepositoryHelper;
import edu.kit.kastel.mcse.ardoco.core.execution.ArDoCo;
import edu.kit.kastel.mcse.ardoco.core.execution.runner.ArDoCoRunner;
import edu.kit.kastel.mcse.ardoco.tlr.codetraceability.SadCodeTraceabilityLinkRecovery;
import edu.kit.kastel.mcse.ardoco.tlr.connectiongenerator.ConnectionGenerator;
import edu.kit.kastel.mcse.ardoco.tlr.models.agents.ArCoTLModelProviderAgent;
import edu.kit.kastel.mcse.ardoco.tlr.recommendationgenerator.RecommendationGenerator;
import edu.kit.kastel.mcse.ardoco.tlr.text.providers.TextPreprocessingAgent;
import edu.kit.kastel.mcse.ardoco.tlr.textextraction.TextExtraction;

/**
 * Extended ArDoCo runner for SAD-Code traceability link recovery.
 * 
 * MODIFIED: Added comprehensive replication support, statistics tracking,
 * multiple run capabilities, and detailed logging.
 */
public class ArDoCoForSadCodeTraceabilityLinkRecovery extends ArDoCoRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ArDoCoForSadCodeTraceabilityLinkRecovery.class);
    
    // ADDED: Configuration constants
    private static final int DEFAULT_NUM_RUNS = 5;
    private static final String DEFAULT_API_KEY = "sk-DUMMY";
    
    // ADDED: Configuration fields
    private static boolean useLiveLLM = false;
    private static int totalRuns = DEFAULT_NUM_RUNS;
    private static final AtomicInteger currentRun = new AtomicInteger(1);
    
    // ADDED: Statistics tracking fields
    private long pipelineStartTime;
    private long pipelineEndTime;
    private int totalAgentsExecuted = 0;
    private String currentProjectName;
    
    // ADDED: Result storage for multiple runs
    private static final SortedMap<Integer, PipelineResult> runResults = new java.util.TreeMap<>();
    
    /**
     * Inner class to store pipeline execution results for each run.
     */
    public static class PipelineResult {
        public final int runNumber;
        public final long durationMs;
        public final int agentsExecuted;
        public final boolean liveLLMMode;
        public final String projectName;
        public final long timestamp;
        
        public PipelineResult(int runNumber, long durationMs, int agentsExecuted, boolean liveLLMMode, String projectName) {
            this.runNumber = runNumber;
            this.durationMs = durationMs;
            this.agentsExecuted = agentsExecuted;
            this.liveLLMMode = liveLLMMode;
            this.projectName = projectName;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("Run %d: %d ms, Agents: %d, LiveLLM: %s, Project: %s",
                runNumber, durationMs, agentsExecuted, liveLLMMode, projectName);
        }
        
        public String toCSV() {
            return String.format("%d,%d,%d,%b,%s,%d",
                runNumber, durationMs, agentsExecuted, liveLLMMode, projectName, timestamp);
        }
        
        public static String getCSVHeader() {
            return "RunNumber,DurationMs,AgentsExecuted,LiveLLMMode,ProjectName,Timestamp";
        }
    }

    public ArDoCoForSadCodeTraceabilityLinkRecovery(String projectName) {
        super(projectName);
        this.currentProjectName = projectName;
        logger.info("Initialized ArDoCoForSadCodeTraceabilityLinkRecovery for project: {}", projectName);
    }

    public void setUp(File inputText, File inputCode, SortedMap<String, String> additionalConfigs, File outputDir) {
        
        // ADDED: Extract configuration from additionalConfigs
        extractConfiguration(additionalConfigs);
        
        // ADDED: Log setup information
        logger.info("========================================");
        logger.info("Setting up SAD-Code Pipeline for Replication");
        logger.info("========================================");
        logger.info("Project: {}", currentProjectName);
        logger.info("Live LLM Mode: {}", useLiveLLM);
        logger.info("Current Run: {}/{}", currentRun.get(), totalRuns);
        logger.info("Input Text: {}", inputText.getAbsolutePath());
        logger.info("Input Code: {}", inputCode.getAbsolutePath());
        logger.info("Output Directory: {}", outputDir.getAbsolutePath());
        logger.info("========================================");
        
        definePipeline(inputText, inputCode, additionalConfigs);
        setOutputDirectory(outputDir);
        isSetUp = true;
        
        logger.info("SAD-Code pipeline setup complete for run {}/{}", currentRun.get(), totalRuns);
    }
    
    // ADDED: Configuration extraction method
    private void extractConfiguration(SortedMap<String, String> additionalConfigs) {
        if (additionalConfigs != null) {
            if (additionalConfigs.containsKey("useLiveLLM")) {
                useLiveLLM = Boolean.parseBoolean(additionalConfigs.get("useLiveLLM"));
            }
            if (additionalConfigs.containsKey("totalRuns")) {
                totalRuns = Integer.parseInt(additionalConfigs.get("totalRuns"));
            }
            if (additionalConfigs.containsKey("currentRun")) {
                currentRun.set(Integer.parseInt(additionalConfigs.get("currentRun")));
            }
        }
        
        // ADDED: Set up environment for live LLM mode
        if (!useLiveLLM) {
            System.setProperty("OPENAI_API_KEY", DEFAULT_API_KEY);
            logger.info("Using cached responses (API key set to {})", DEFAULT_API_KEY);
        } else {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.equals(DEFAULT_API_KEY)) {
                logger.warn("OPENAI_API_KEY not properly set for live mode! Set using: export OPENAI_API_KEY=your-key");
            } else {
                logger.info("Live mode enabled. API key detected (length: {} chars)", apiKey.length());
            }
        }
    }

    private void definePipeline(File inputText, File inputCode, SortedMap<String, String> additionalConfigs) {
        
        pipelineStartTime = System.currentTimeMillis();
        
        ArDoCo arDoCo = this.getArDoCo();
        var dataRepository = arDoCo.getDataRepository();

        // ADDED: Store configuration in data repository for other components
        dataRepository.addData("config.useLiveLLM", useLiveLLM);
        dataRepository.addData("config.currentRun", currentRun.get());
        dataRepository.addData("config.totalRuns", totalRuns);
        dataRepository.addData("config.currentProject", currentProjectName);
        dataRepository.addData("config.task", "SAD-CODE");

        var text = CommonUtilities.readInputText(inputText);
        if (text.isBlank()) {
            throw new IllegalArgumentException("Cannot deal with empty input text. Maybe there was an error reading the file.");
        }
        DataRepositoryHelper.putInputText(dataRepository, text);
        
        // ADDED: Log input information
        logger.info("Input text loaded: {} characters", text.length());
        logger.info("Input code path: {}", inputCode.getAbsolutePath());
        
        // ADDED: Track pipeline steps
        logger.info("Building SAD-Code pipeline with agents...");
        
        var codeConfiguration = ArCoTLModelProviderAgent.getCodeConfiguration(inputCode);
        ArCoTLModelProviderAgent arCoTLModelProviderAgent = ArCoTLModelProviderAgent.getArCoTLModelProviderAgent(dataRepository, additionalConfigs, null,
                codeConfiguration);
        arDoCo.addPipelineStep(arCoTLModelProviderAgent);
        totalAgentsExecuted++;
        logger.debug("  [{}] Added ArCoTLModelProviderAgent", totalAgentsExecuted);

        arDoCo.addPipelineStep(TextPreprocessingAgent.get(additionalConfigs, dataRepository));
        totalAgentsExecuted++;
        logger.debug("  [{}] Added TextPreprocessingAgent", totalAgentsExecuted);

        arDoCo.addPipelineStep(TextExtraction.get(additionalConfigs, dataRepository));
        totalAgentsExecuted++;
        logger.debug("  [{}] Added TextExtraction", totalAgentsExecuted);
        
        arDoCo.addPipelineStep(RecommendationGenerator.get(additionalConfigs, dataRepository));
        totalAgentsExecuted++;
        logger.debug("  [{}] Added RecommendationGenerator", totalAgentsExecuted);
        
        arDoCo.addPipelineStep(ConnectionGenerator.get(additionalConfigs, dataRepository));
        totalAgentsExecuted++;
        logger.debug("  [{}] Added ConnectionGenerator", totalAgentsExecuted);
        
        arDoCo.addPipelineStep(SadCodeTraceabilityLinkRecovery.get(additionalConfigs, dataRepository));
        totalAgentsExecuted++;
        logger.debug("  [{}] Added SadCodeTraceabilityLinkRecovery", totalAgentsExecuted);

        logger.info("SAD-Code pipeline built with {} agents", totalAgentsExecuted);
    }
    
    // ADDED: Enhanced run method with statistics tracking
    @Override
    public void run() {
        logger.info("========================================");
        logger.info("Executing SAD-Code Pipeline - Run {}/{}", currentRun.get(), totalRuns);
        logger.info("========================================");
        logger.info("Project: {}", currentProjectName);
        logger.info("Live LLM Mode: {}", useLiveLLM);
        logger.info("========================================");
        
        long executionStartTime = System.currentTimeMillis();
        
        try {
            super.run();
            pipelineEndTime = System.currentTimeMillis();
            long executionDuration = pipelineEndTime - executionStartTime;
            
            // ADDED: Store result for this run
            PipelineResult result = new PipelineResult(
                currentRun.get(),
                executionDuration,
                totalAgentsExecuted,
                useLiveLLM,
                currentProjectName
            );
            runResults.put(currentRun.get(), result);
            
            logger.info("========================================");
            logger.info("SAD-Code Pipeline Execution Complete - Run {}/{}", currentRun.get(), totalRuns);
            logger.info("========================================");
            logger.info("Execution time: {} ms ({} seconds)", executionDuration, executionDuration / 1000.0);
            logger.info("Agents executed: {}", totalAgentsExecuted);
            logger.info("Average time per agent: {} ms", executionDuration / totalAgentsExecuted);
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("SAD-Code pipeline execution failed for run {}/{}: {}", currentRun.get(), totalRuns, e.getMessage(), e);
            throw e;
        }
    }
    
    // ADDED: Method to run multiple iterations
    public void runMultipleIterations() throws Exception {
        logger.info("========================================");
        logger.info("Starting MULTIPLE RUNS Execution - SAD-CODE");
        logger.info("========================================");
        logger.info("Total runs: {}", totalRuns);
        logger.info("Live LLM Mode: {}", useLiveLLM);
        logger.info("========================================");
        
        for (int run = 1; run <= totalRuns; run++) {
            currentRun.set(run);
            logger.info("\n\n>>> Starting iteration {}/{} <<<\n", run, totalRuns);
            
            // Reset state for new run
            resetForNewRun();
            
            // Execute the pipeline
            run();
            
            // Small delay between runs to avoid rate limiting
            if (run < totalRuns && useLiveLLM) {
                logger.info("Waiting 2 seconds before next run to avoid rate limiting...");
                Thread.sleep(2000);
            }
        }
        
        // Print summary after all runs
        printSummary();
    }
    
    // ADDED: Reset method for multiple runs
    private void resetForNewRun() {
        this.pipelineStartTime = 0;
        this.pipelineEndTime = 0;
        this.totalAgentsExecuted = 0;
        logger.debug("SAD-Code pipeline statistics reset for run {}", currentRun.get());
    }
    
    // ADDED: Print summary of all runs
    public void printSummary() {
        logger.info("\n\n");
        logger.info("########################################");
        logger.info("#     SAD-CODE EXECUTION SUMMARY       #");
        logger.info("########################################");
        logger.info("Project: {}", currentProjectName);
        logger.info("Live LLM Mode: {}", useLiveLLM);
        logger.info("Total Runs Completed: {}", runResults.size());
        logger.info("----------------------------------------");
        
        long totalDuration = 0;
        for (PipelineResult result : runResults.values()) {
            totalDuration += result.durationMs;
            logger.info("  {}", result);
        }
        
        long avgDuration = runResults.isEmpty() ? 0 : totalDuration / runResults.size();
        logger.info("----------------------------------------");
        logger.info("Average Duration: {} ms ({} seconds)", avgDuration, avgDuration / 1000.0);
        logger.info("########################################\n\n");
    }
    
    // ADDED: Export results to CSV
    public void exportResultsToCSV(String filepath) throws java.io.IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(filepath)) {
            writer.println(PipelineResult.getCSVHeader());
            for (PipelineResult result : runResults.values()) {
                writer.println(result.toCSV());
            }
        }
        logger.info("SAD-CODE results exported to: {}", filepath);
    }
    
    // ADDED: Get statistics as string
    public String getStatistics() {
        long totalDuration = pipelineEndTime - pipelineStartTime;
        return String.format(
            "Task: SAD-CODE, Project: %s, LiveLLM: %s, Run: %d/%d, Duration: %d ms, Agents: %d",
            currentProjectName,
            useLiveLLM,
            currentRun.get(),
            totalRuns,
            totalDuration,
            totalAgentsExecuted
        );
    }
    
    // ADDED: Get all results
    public static SortedMap<Integer, PipelineResult> getAllResults() {
        return new java.util.TreeMap<>(runResults);
    }
    
    // ADDED: Clear results for new batch
    public static void clearResults() {
        runResults.clear();
        logger.info("Cleared all stored SAD-CODE pipeline results");
    }
    
    // ADDED: Setter methods for configuration
    public static void setUseLiveLLM(boolean useLive) {
        useLiveLLM = useLive;
        logger.info("Live LLM mode set to: {}", useLiveLLM);
    }
    
    public static void setTotalRuns(int runs) {
        if (runs > 0) {
            totalRuns = runs;
            logger.info("Total runs set to: {}", totalRuns);
        } else {
            logger.warn("Invalid total runs value: {}, keeping previous value {}", runs, totalRuns);
        }
    }
    
    public static int getTotalRuns() {
        return totalRuns;
    }
    
    public static int getCurrentRunNumber() {
        return currentRun.get();
    }
    
    public static boolean isUseLiveLLM() {
        return useLiveLLM;
    }
    
    // ADDED: Reset static configuration
    public static void resetConfiguration() {
        useLiveLLM = false;
        totalRuns = DEFAULT_NUM_RUNS;
        currentRun.set(1);
        clearResults();
        logger.info("Configuration reset to defaults (LiveLLM: false, TotalRuns: {})", DEFAULT_NUM_RUNS);
    }
}
