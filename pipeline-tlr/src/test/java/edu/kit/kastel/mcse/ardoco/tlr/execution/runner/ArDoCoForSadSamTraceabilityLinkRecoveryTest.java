/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.tlr.execution.runner;

import java.io.File;
import java.io.PrintWriter;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.core.api.models.ArchitectureModelType;
import edu.kit.kastel.mcse.ardoco.tlr.execution.ArDoCoForSadSamTraceabilityLinkRecovery;
import edu.kit.kastel.mcse.ardoco.core.execution.ConfigurationHelper;
import edu.kit.kastel.mcse.ardoco.core.execution.RunnerBaseTest;

@Disabled("Disabled as other (integration) tests cover the same functionality. Enable for debugging/local development.")
class ArDoCoForSadSamTraceabilityLinkRecoveryTest extends RunnerBaseTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ArDoCoForSadSamTraceabilityLinkRecoveryTest.class);
    
    // ADDED: Configuration constants
    private static final int DEFAULT_NUM_RUNS = 5;
    private static final String RESULTS_DIR = "results/sad_sam_runs";
    
    // ADDED: Statistics storage for PCM and UML runs separately
    private static final SortedMap<Integer, RunResult> pcmRunResults = new TreeMap<>();
    private static final SortedMap<Integer, RunResult> umlRunResults = new TreeMap<>();
    
    // ADDED: Inner class to store run results
    private static class RunResult {
        final int runNumber;
        final long durationMs;
        final boolean success;
        final String errorMessage;
        final long timestamp;
        final ArchitectureModelType modelType;
        
        RunResult(int runNumber, long durationMs, boolean success, String errorMessage, ArchitectureModelType modelType) {
            this.runNumber = runNumber;
            this.durationMs = durationMs;
            this.success = success;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
            this.modelType = modelType;
        }
        
        @Override
        public String toString() {
            return String.format("Run %d (%s): %s - Duration: %d ms", 
                runNumber, modelType, success ? "SUCCESS" : "FAILED", durationMs);
        }
        
        String toCSV() {
            return String.format("%d,%s,%d,%b,%s,%d", 
                runNumber, modelType, durationMs, success, 
                errorMessage != null ? errorMessage.replace(",", ";") : "", timestamp);
        }
        
        static String getCSVHeader() {
            return "RunNumber,ModelType,DurationMs,Success,ErrorMessage,Timestamp";
        }
    }

    // ==================== PCM MODEL TESTS ====================
    
    @Test
    @DisplayName("Test ArDoCo for SAD-SAM-TLR (PCM) - Single Run")
    void testSadSamTlrPcm() {
        logger.info("========================================");
        logger.info("Starting SAD-SAM TLR Test (PCM) - Single Run");
        logger.info("========================================");
        
        var runner = new ArDoCoForSadSamTraceabilityLinkRecovery(projectName);
        var additionalConfigsMap = ConfigurationHelper.loadAdditionalConfigs(new File(additionalConfigs));
        
        // ADDED: Set live LLM mode from system property
        boolean useLiveLLM = Boolean.parseBoolean(System.getProperty("useLiveLLM", "false"));
        if (useLiveLLM) {
            additionalConfigsMap.put("useLiveLLM", "true");
            logger.warn("LIVE LLM MODE ENABLED - API calls will incur costs!");
        }
        
        runner.setUp(inputText, inputModelArchitecture, ArchitectureModelType.PCM, additionalConfigsMap, outputDir);

        long startTime = System.currentTimeMillis();
        var result = runner.run();
        long duration = System.currentTimeMillis() - startTime;
        
        testRunnerAssertions(runner);
        Assertions.assertNotNull(result);
        
        logger.info("PCM single run completed - Duration: {} ms", duration);
        logger.info("========================================");
    }
    
    // ADDED: Repeated test for PCM multiple runs
    @RepeatedTest(value = DEFAULT_NUM_RUNS, name = "PCM Run {currentRepetition} of {totalRepetitions}")
    @DisplayName("Test ArDoCo for SAD-SAM-TLR (PCM) - Multiple Runs")
    void testSadSamTlrPcmMultipleRuns(RepetitionInfo repetitionInfo, TestInfo testInfo) {
        int currentRun = repetitionInfo.getCurrentRepetition();
        int totalRuns = repetitionInfo.getTotalRepetitions();
        
        logger.info("========================================");
        logger.info("Starting PCM SAD-SAM TLR Test - Run {}/{}", currentRun, totalRuns);
        logger.info("========================================");
        
        var runner = new ArDoCoForSadSamTraceabilityLinkRecovery(projectName);
        var additionalConfigsMap = ConfigurationHelper.loadAdditionalConfigs(new File(additionalConfigs));
        
        // ADDED: Set configuration for this run
        additionalConfigsMap.put("currentRun", String.valueOf(currentRun));
        additionalConfigsMap.put("totalRuns", String.valueOf(totalRuns));
        additionalConfigsMap.put("currentModelType", "PCM");
        
        boolean useLiveLLM = Boolean.parseBoolean(System.getProperty("useLiveLLM", "false"));
        if (useLiveLLM) {
            additionalConfigsMap.put("useLiveLLM", "true");
            if (currentRun == 1) {
                logger.warn("LIVE LLM MODE ENABLED - API calls will incur costs!");
            }
        }
        
        runner.setUp(inputText, inputModelArchitecture, ArchitectureModelType.PCM, additionalConfigsMap, outputDir);

        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        
        try {
            var result = runner.run();
            testRunnerAssertions(runner);
            Assertions.assertNotNull(result);
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            logger.error("PCM Run {} failed: {}", currentRun, errorMessage);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            pcmRunResults.put(currentRun, new RunResult(currentRun, duration, success, errorMessage, ArchitectureModelType.PCM));
            
            logger.info("PCM Run {}/{} completed - Success: {}, Duration: {} ms", 
                currentRun, totalRuns, success, duration);
        }
        
        // ADDED: Small delay between runs to avoid rate limiting
        if (currentRun < totalRuns && useLiveLLM) {
            try {
                logger.info("Waiting 2 seconds before next PCM run to avoid rate limiting...");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting between runs");
            }
        }
    }
    
    // ADDED: Test with live LLM mode for PCM
    @Test
    @DisplayName("Test ArDoCo for SAD-SAM-TLR (PCM) - Live LLM Mode")
    void testSadSamTlrPcmLiveLLM() {
        logger.info("========================================");
        logger.info("Starting PCM SAD-SAM TLR Test (LIVE LLM MODE)");
        logger.info("========================================");
        logger.warn("⚠️  This test will make actual API calls and incur costs! ⚠️");
        
        System.setProperty("useLiveLLM", "true");
        
        try {
            testSadSamTlrPcm();
        } finally {
            System.clearProperty("useLiveLLM");
        }
    }
    
    // ADDED: Test with multiple runs configuration for PCM
    @Test
    @DisplayName("Test ArDoCo for SAD-SAM-TLR (PCM) - Configured Multiple Runs")
    void testSadSamTlrPcmWithMultipleRuns() {
        int numRuns = Integer.parseInt(System.getProperty("test.runs", String.valueOf(DEFAULT_NUM_RUNS)));
        logger.info("========================================");
        logger.info("Starting PCM SAD-SAM TLR Test - {} runs configured", numRuns);
        logger.info("========================================");
        
        boolean useLiveLLM = Boolean.parseBoolean(System.getProperty("useLiveLLM", "false"));
        
        for (int run = 1; run <= numRuns; run++) {
            var runner = new ArDoCoForSadSamTraceabilityLinkRecovery(projectName);
            var additionalConfigsMap = ConfigurationHelper.loadAdditionalConfigs(new File(additionalConfigs));
            
            additionalConfigsMap.put("currentRun", String.valueOf(run));
            additionalConfigsMap.put("totalRuns", String.valueOf(numRuns));
            additionalConfigsMap.put("currentModelType", "PCM");
            
            if (useLiveLLM) {
                additionalConfigsMap.put("useLiveLLM", "true");
            }
            
            runner.setUp(inputText, inputModelArchitecture, ArchitectureModelType.PCM, additionalConfigsMap, outputDir);
            
            long startTime = System.currentTimeMillis();
            var result = runner.run();
            long duration = System.currentTimeMillis() - startTime;
            
            testRunnerAssertions(runner);
            Assertions.assertNotNull(result);
            
            logger.info("PCM Run {} completed - Duration: {} ms", run, duration);
            
            if (run < numRuns && useLiveLLM) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        logger.info("All {} PCM runs completed successfully", numRuns);
    }

    // ==================== UML MODEL TESTS ====================
    
    @Disabled("Disabled for faster builds. Enable if you need to check UML models.")
    @Test
    @DisplayName("Test ArDoCo for SAD-SAM-TLR (UML) - Single Run")
    void testSadSamTlrUml() {
        logger.info("========================================");
        logger.info("Starting SAD-SAM TLR Test (UML) - Single Run");
        logger.info("========================================");
        
        var runner = new ArDoCoForSadSamTraceabilityLinkRecovery(projectName);
        var additionalConfigsMap = ConfigurationHelper.loadAdditionalConfigs(new File(additionalConfigs));
        
        boolean useLiveLLM = Boolean.parseBoolean(System.getProperty("useLiveLLM", "false"));
        if (useLiveLLM) {
            additionalConfigsMap.put("useLiveLLM", "true");
            logger.warn("LIVE LLM MODE ENABLED - API calls will incur costs!");
        }
        
        runner.setUp(inputText, inputModelArchitectureUml, ArchitectureModelType.UML, additionalConfigsMap, outputDir);

        long startTime = System.currentTimeMillis();
        var result = runner.run();
        long duration = System.currentTimeMillis() - startTime;
        
        testRunnerAssertions(runner);
        Assertions.assertNotNull(result);
        
        logger.info("UML single run completed - Duration: {} ms", duration);
        logger.info("========================================");
    }
    
    // ADDED: Repeated test for UML multiple runs (disabled by default)
    @Disabled("Disabled for faster builds. Enable to test UML with multiple runs.")
    @RepeatedTest(value = DEFAULT_NUM_RUNS, name = "UML Run {currentRepetition} of {totalRepetitions}")
    @DisplayName("Test ArDoCo for SAD-SAM-TLR (UML) - Multiple Runs")
    void testSadSamTlrUmlMultipleRuns(RepetitionInfo repetitionInfo, TestInfo testInfo) {
        int currentRun = repetitionInfo.getCurrentRepetition();
        int totalRuns = repetitionInfo.getTotalRepetitions();
        
        logger.info("========================================");
        logger.info("Starting UML SAD-SAM TLR Test - Run {}/{}", currentRun, totalRuns);
        logger.info("========================================");
        
        var runner = new ArDoCoForSadSamTraceabilityLinkRecovery(projectName);
        var additionalConfigsMap = ConfigurationHelper.loadAdditionalConfigs(new File(additionalConfigs));
        
        additionalConfigsMap.put("currentRun", String.valueOf(currentRun));
        additionalConfigsMap.put("totalRuns", String.valueOf(totalRuns));
        additionalConfigsMap.put("currentModelType", "UML");
        
        boolean useLiveLLM = Boolean.parseBoolean(System.getProperty("useLiveLLM", "false"));
        if (useLiveLLM) {
            additionalConfigsMap.put("useLiveLLM", "true");
            if (currentRun == 1) {
                logger.warn("LIVE LLM MODE ENABLED - API calls will incur costs!");
            }
        }
        
        runner.setUp(inputText, inputModelArchitectureUml, ArchitectureModelType.UML, additionalConfigsMap, outputDir);

        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        
        try {
            var result = runner.run();
            testRunnerAssertions(runner);
            Assertions.assertNotNull(result);
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            logger.error("UML Run {} failed: {}", currentRun, errorMessage);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            umlRunResults.put(currentRun, new RunResult(currentRun, duration, success, errorMessage, ArchitectureModelType.UML));
            
            logger.info("UML Run {}/{} completed - Success: {}, Duration: {} ms", 
                currentRun, totalRuns, success, duration);
        }
        
        if (currentRun < totalRuns && useLiveLLM) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // ADDED: Test with live LLM mode for UML
    @Disabled("Disabled for faster builds. Enable to test UML with live LLM.")
    @Test
    @DisplayName("Test ArDoCo for SAD-SAM-TLR (UML) - Live LLM Mode")
    void testSadSamTlrUmlLiveLLM() {
        logger.info("========================================");
        logger.info("Starting UML SAD-SAM TLR Test (LIVE LLM MODE)");
        logger.info("========================================");
        logger.warn("⚠️  This test will make actual API calls and incur costs! ⚠️");
        
        System.setProperty("useLiveLLM", "true");
        
        try {
            testSadSamTlrUml();
        } finally {
            System.clearProperty("useLiveLLM");
        }
    }
    
    // ADDED: Test with multiple runs configuration for UML (disabled)
    @Disabled("Disabled for faster builds. Enable to test UML with multiple runs.")
    @Test
    @DisplayName("Test ArDoCo for SAD-SAM-TLR (UML) - Configured Multiple Runs")
    void testSadSamTlrUmlWithMultipleRuns() {
        int numRuns = Integer.parseInt(System.getProperty("test.runs", String.valueOf(DEFAULT_NUM_RUNS)));
        logger.info("========================================");
        logger.info("Starting UML SAD-SAM TLR Test - {} runs configured", numRuns);
        logger.info("========================================");
        
        boolean useLiveLLM = Boolean.parseBoolean(System.getProperty("useLiveLLM", "false"));
        
        for (int run = 1; run <= numRuns; run++) {
            var runner = new ArDoCoForSadSamTraceabilityLinkRecovery(projectName);
            var additionalConfigsMap = ConfigurationHelper.loadAdditionalConfigs(new File(additionalConfigs));
            
            additionalConfigsMap.put("currentRun", String.valueOf(run));
            additionalConfigsMap.put("totalRuns", String.valueOf(numRuns));
            additionalConfigsMap.put("currentModelType", "UML");
            
            if (useLiveLLM) {
                additionalConfigsMap.put("useLiveLLM", "true");
            }
            
            runner.setUp(inputText, inputModelArchitectureUml, ArchitectureModelType.UML, additionalConfigsMap, outputDir);
            
            long startTime = System.currentTimeMillis();
            var result = runner.run();
            long duration = System.currentTimeMillis() - startTime;
            
            testRunnerAssertions(runner);
            Assertions.assertNotNull(result);
            
            logger.info("UML Run {} completed - Duration: {} ms", run, duration);
            
            if (run < numRuns && useLiveLLM) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        logger.info("All {} UML runs completed successfully", numRuns);
    }

    // ==================== SUMMARY AND UTILITY METHODS ====================
    
    // ADDED: Method to print summary after all runs
    @org.junit.jupiter.api.AfterAll
    static void printSummary() {
        printSummaryForModelType(pcmRunResults, "PCM");
        printSummaryForModelType(umlRunResults, "UML");
    }
    
    private static void printSummaryForModelType(SortedMap<Integer, RunResult> results, String modelType) {
        if (results.isEmpty()) {
            logger.info("No {} multiple run results to summarize", modelType);
            return;
        }
        
        logger.info("\n\n");
        logger.info("########################################");
        logger.info("#     SAD-SAM TLR TEST SUMMARY         #");
        logger.info("#        Model Type: {}                #", modelType);
        logger.info("########################################");
        logger.info("Total Runs: {}", results.size());
        logger.info("----------------------------------------");
        
        long totalDuration = 0;
        int successCount = 0;
        int failCount = 0;
        
        for (RunResult result : results.values()) {
            totalDuration += result.durationMs;
            if (result.success) {
                successCount++;
            } else {
                failCount++;
            }
            logger.info("  {}", result);
        }
        
        long avgDuration = results.isEmpty() ? 0 : totalDuration / results.size();
        logger.info("----------------------------------------");
        logger.info("Successful Runs: {}", successCount);
        logger.info("Failed Runs: {}", failCount);
        logger.info("Total Duration: {} ms", totalDuration);
        logger.info("Average Duration: {} ms", avgDuration);
        logger.info("########################################\n\n");
        
        // Export results to CSV
        exportResultsToCSV(results, modelType);
    }
    
    // ADDED: Export results to CSV
    private static void exportResultsToCSV(SortedMap<Integer, RunResult> results, String modelType) {
        try {
            File resultsDir = new File(RESULTS_DIR);
            if (!resultsDir.exists()) {
                resultsDir.mkdirs();
            }
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            File csvFile = new File(resultsDir, "sad_sam_results_" + modelType + "_" + timestamp + ".csv");
            
            try (PrintWriter writer = new PrintWriter(csvFile)) {
                writer.println(RunResult.getCSVHeader());
                for (RunResult result : results.values()) {
                    writer.println(result.toCSV());
                }
            }
            
            logger.info("Results for {} exported to: {}", modelType, csvFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to export {} results to CSV: {}", modelType, e.getMessage());
        }
    }
    
    // ADDED: Helper method to clear results
    public static void clearResults() {
        pcmRunResults.clear();
        umlRunResults.clear();
        logger.info("Cleared all stored test results");
    }
    
    // ADDED: Helper method to get PCM results
    public static SortedMap<Integer, RunResult> getPcmResults() {
        return new TreeMap<>(pcmRunResults);
    }
    
    // ADDED: Helper method to get UML results
    public static SortedMap<Integer, RunResult> getUmlResults() {
        return new TreeMap<>(umlRunResults);
    }
}
