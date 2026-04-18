/* Licensed under MIT 2026-2027. */
package edu.kit.kastel.mcse.ardoco.tlr.execution;

import java.io.File;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.core.api.models.ArchitectureModelType;
import edu.kit.kastel.mcse.ardoco.core.execution.ArDoCo;
import edu.kit.kastel.mcse.ardoco.core.execution.runner.ArDoCoRunner;
import edu.kit.kastel.mcse.ardoco.tlr.codetraceability.SamCodeTraceabilityLinkRecovery;
import edu.kit.kastel.mcse.ardoco.tlr.models.agents.ArCoTLModelProviderAgent;
import edu.kit.kastel.mcse.ardoco.tlr.models.agents.ArchitectureConfiguration;

public class ArDoCoForSamCodeTraceabilityLinkRecovery extends ArDoCoRunner {

    private static final Logger logger = LoggerFactory.getLogger(ArDoCoForSamCodeTraceabilityLinkRecovery.class);

    // ADDED: Configuration fields for tracking
    private boolean useLiveLLM = false;
    private int currentRun = 1;
    private int totalRuns = 1;
    private String currentModel = "unknown";
    private String currentProjectName;
    private ArchitectureModelType architectureModelType;

    // ADDED: Statistics tracking fields
    private long pipelineStartTime;
    private long pipelineEndTime;
    private int totalAgentsExecuted = 0;

    public ArDoCoForSamCodeTraceabilityLinkRecovery(String projectName) {
        super(projectName);
        this.currentProjectName = projectName;
        logger.info("Initialized ArDoCoForSamCodeTraceabilityLinkRecovery for project: {}", projectName);
    }

    public void setUp(File inputArchitectureModel, ArchitectureModelType architectureModelType, File inputCode, SortedMap<String, String> additionalConfigs,
            File outputDir) {
        
        // ADDED: Store architecture model type
        this.architectureModelType = architectureModelType;
        
        // ADDED: Extract configuration values
        extractConfiguration(additionalConfigs);
        
        logger.info("=== Setting up SAM-Code Pipeline ===");
        logger.info("Project: {}", currentProjectName);
        logger.info("Model: {}", currentModel);
        logger.info("Architecture Model Type: {}", architectureModelType);
        logger.info("Live LLM Mode: {}", useLiveLLM);
        logger.info("Current Run: {}/{}", currentRun, totalRuns);
        logger.info("Input Model: {}", inputArchitectureModel.getAbsolutePath());
        logger.info("Input Code: {}", inputCode.getAbsolutePath());
        logger.info("Output Directory: {}", outputDir.getAbsolutePath());
        
        definePipeline(inputArchitectureModel, architectureModelType, inputCode, additionalConfigs);
        setOutputDirectory(outputDir);
        isSetUp = true;
        
        logger.info("SAM-Code pipeline setup complete");
    }

    // ADDED: Configuration extraction method
    private void extractConfiguration(SortedMap<String, String> additionalConfigs) {
        if (additionalConfigs != null) {
            if (additionalConfigs.containsKey("useLiveLLM")) {
                this.useLiveLLM = Boolean.parseBoolean(additionalConfigs.get("useLiveLLM"));
            }
            if (additionalConfigs.containsKey("currentRun")) {
                this.currentRun = Integer.parseInt(additionalConfigs.get("currentRun"));
            }
            if (additionalConfigs.containsKey("totalRuns")) {
                this.totalRuns = Integer.parseInt(additionalConfigs.get("totalRuns"));
            }
            if (additionalConfigs.containsKey("currentModel")) {
                this.currentModel = additionalConfigs.get("currentModel");
            }
            if (additionalConfigs.containsKey("currentProject")) {
                this.currentProjectName = additionalConfigs.get("currentProject");
            }
        }
        
        // ADDED: Log configuration
        logger.debug("Configuration extracted - LiveLLM: {}, Run: {}/{}, Model: {}", 
            useLiveLLM, currentRun, totalRuns, currentModel);
    }

    private void definePipeline(File inputArchitectureModel, ArchitectureModelType architectureModelType, File inputCode,
            SortedMap<String, String> additionalConfigs) {
        
        pipelineStartTime = System.currentTimeMillis();
        
        ArDoCo arDoCo = this.getArDoCo();
        var dataRepository = arDoCo.getDataRepository();

        // ADDED: Store configuration in data repository for other components
        dataRepository.addData("config.useLiveLLM", useLiveLLM);
        dataRepository.addData("config.currentRun", currentRun);
        dataRepository.addData("config.currentModel", currentModel);
        dataRepository.addData("config.currentProject", currentProjectName);
        dataRepository.addData("config.task", "SAM-CODE");

        logger.info("Building SAM-Code traceability pipeline...");

        var codeConfiguration = ArCoTLModelProviderAgent.getCodeConfiguration(inputCode);
        var architectureConfiguration = new ArchitectureConfiguration(inputArchitectureModel, architectureModelType);

        ArCoTLModelProviderAgent arCoTLModelProviderAgent = ArCoTLModelProviderAgent.getArCoTLModelProviderAgent(dataRepository, additionalConfigs,
                architectureConfiguration, codeConfiguration);
        arDoCo.addPipelineStep(arCoTLModelProviderAgent);
        totalAgentsExecuted++;
        logger.debug("Added ArCoTLModelProviderAgent");

        // ADDED: Configure SamCodeTraceabilityLinkRecovery with tracking info
        var samCodeTraceability = SamCodeTraceabilityLinkRecovery.get(additionalConfigs, dataRepository);
        arDoCo.addPipelineStep(samCodeTraceability);
        totalAgentsExecuted++;
        logger.debug("Added SamCodeTraceabilityLinkRecovery");

        logger.info("Pipeline defined with {} agents", totalAgentsExecuted);
    }

    // ADDED: Method to run pipeline with statistics
    @Override
    public void run() {
        logger.info("========================================");
        logger.info("Starting SAM-Code Pipeline Execution");
        logger.info("========================================");
        logger.info("Project: {}", currentProjectName);
        logger.info("Model: {}", currentModel);
        logger.info("Architecture Type: {}", architectureModelType);
        logger.info("Live LLM Mode: {}", useLiveLLM);
        logger.info("Run: {}/{}", currentRun, totalRuns);
        logger.info("========================================");
        
        long executionStartTime = System.currentTimeMillis();
        
        try {
            super.run();
            pipelineEndTime = System.currentTimeMillis();
            long executionDuration = pipelineEndTime - executionStartTime;
            
            logger.info("========================================");
            logger.info("SAM-Code Pipeline Execution Complete");
            logger.info("========================================");
            logger.info("Total execution time: {} ms ({} seconds)", 
                executionDuration, executionDuration / 1000.0);
            logger.info("Total agents executed: {}", totalAgentsExecuted);
            logger.info("Average time per agent: {} ms", executionDuration / totalAgentsExecuted);
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("SAM-Code pipeline execution failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ADDED: Method to get execution statistics
    public String getStatistics() {
        long totalDuration = pipelineEndTime - pipelineStartTime;
        return String.format(
            "Task: SAM-CODE, Project: %s, Model: %s, LiveLLM: %s, Run: %d/%d, Duration: %d ms, Agents: %d",
            currentProjectName,
            currentModel,
            useLiveLLM,
            currentRun,
            totalRuns,
            totalDuration,
            totalAgentsExecuted
        );
    }

    // ADDED: Method to get CSV formatted statistics
    public String toCSV() {
        long totalDuration = pipelineEndTime - pipelineStartTime;
        return String.format(
            "%s,%s,%s,%b,%d,%d,%d,%d",
            "SAM-CODE",
            currentProjectName,
            currentModel,
            useLiveLLM,
            currentRun,
            totalRuns,
            totalDuration,
            totalAgentsExecuted
        );
    }

    // ADDED: Static method to get CSV header
    public static String getCSVHeader() {
        return "Task,Project,Model,LiveLLM,CurrentRun,TotalRuns,DurationMs,AgentsExecuted";
    }

    // ADDED: Getters for configuration
    public boolean isUseLiveLLM() {
        return useLiveLLM;
    }

    public int getCurrentRun() {
        return currentRun;
    }

    public int getTotalRuns() {
        return totalRuns;
    }

    public String getCurrentModel() {
        return currentModel;
    }

    public String getCurrentProjectName() {
        return currentProjectName;
    }

    public ArchitectureModelType getArchitectureModelType() {
        return architectureModelType;
    }

    // ADDED: Setters for programmatic configuration
    public void setUseLiveLLM(boolean useLiveLLM) {
        this.useLiveLLM = useLiveLLM;
        logger.info("Live LLM mode set to: {}", useLiveLLM);
    }

    public void setCurrentRun(int currentRun) {
        this.currentRun = currentRun;
    }

    public void setTotalRuns(int totalRuns) {
        this.totalRuns = totalRuns;
    }

    public void setCurrentModel(String currentModel) {
        this.currentModel = currentModel;
    }

    // ADDED: Reset method for multiple runs
    public void reset() {
        this.pipelineStartTime = 0;
        this.pipelineEndTime = 0;
        this.totalAgentsExecuted = 0;
        logger.info("SAM-Code pipeline statistics reset for run {}", currentRun);
    }
}
