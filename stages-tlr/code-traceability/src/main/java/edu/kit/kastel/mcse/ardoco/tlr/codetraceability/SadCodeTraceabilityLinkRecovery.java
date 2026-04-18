/* Licensed under MIT 2026-2027. */
package edu.kit.kastel.mcse.ardoco.tlr.codetraceability;

import java.util.List;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.core.api.codetraceability.CodeTraceabilityState;
import edu.kit.kastel.mcse.ardoco.tlr.codetraceability.agents.ArchitectureLinkToCodeLinkTransformerAgent;
import edu.kit.kastel.mcse.ardoco.core.common.util.DataRepositoryHelper;
import edu.kit.kastel.mcse.ardoco.core.data.DataRepository;
import edu.kit.kastel.mcse.ardoco.core.pipeline.AbstractExecutionStage;

public class SadCodeTraceabilityLinkRecovery extends AbstractExecutionStage {

    private static final Logger logger = LoggerFactory.getLogger(SadCodeTraceabilityLinkRecovery.class);

    // ADDED: Configuration fields for live LLM tracking
    private boolean useLiveLLM = false;
    private int currentRun = 1;
    private String currentModel = "unknown";
    private String currentProject = "unknown";

    public SadCodeTraceabilityLinkRecovery(DataRepository dataRepository) {
        super(List.of(new ArchitectureLinkToCodeLinkTransformerAgent(dataRepository)), 
              SadCodeTraceabilityLinkRecovery.class.getSimpleName(), 
              dataRepository);
    }

    public static SadCodeTraceabilityLinkRecovery get(SortedMap<String, String> additionalConfigs, DataRepository dataRepository) {
        var sadSamCodeTraceabilityLinkRecovery = new SadCodeTraceabilityLinkRecovery(dataRepository);
        sadSamCodeTraceabilityLinkRecovery.applyConfiguration(additionalConfigs);
        
        // ADDED: Extract configuration values for live LLM tracking
        if (additionalConfigs != null) {
            if (additionalConfigs.containsKey("useLiveLLM")) {
                sadSamCodeTraceabilityLinkRecovery.useLiveLLM = Boolean.parseBoolean(additionalConfigs.get("useLiveLLM"));
            }
            if (additionalConfigs.containsKey("currentRun")) {
                sadSamCodeTraceabilityLinkRecovery.currentRun = Integer.parseInt(additionalConfigs.get("currentRun"));
            }
            if (additionalConfigs.containsKey("currentModel")) {
                sadSamCodeTraceabilityLinkRecovery.currentModel = additionalConfigs.get("currentModel");
            }
            if (additionalConfigs.containsKey("currentProject")) {
                sadSamCodeTraceabilityLinkRecovery.currentProject = additionalConfigs.get("currentProject");
            }
        }
        
        logger.info("SadCodeTraceabilityLinkRecovery initialized - Run: {}, Model: {}, Project: {}, LiveLLM: {}", 
            sadSamCodeTraceabilityLinkRecovery.currentRun,
            sadSamCodeTraceabilityLinkRecovery.currentModel,
            sadSamCodeTraceabilityLinkRecovery.currentProject,
            sadSamCodeTraceabilityLinkRecovery.useLiveLLM);
        
        return sadSamCodeTraceabilityLinkRecovery;
    }

    @Override
    protected void initializeState() {
        DataRepository dataRepository = getDataRepository();
        
        // ADDED: Log initialization
        logger.info("Initializing CodeTraceabilityState for SAD-Code traceability recovery");
        
        if (!DataRepositoryHelper.hasCodeTraceabilityState(dataRepository)) {
            var codeTraceabilityState = new CodeTraceabilityStateImpl();
            
            // ADDED: Configure the state with tracking information
            if (codeTraceabilityState instanceof CodeTraceabilityStateImpl) {
                CodeTraceabilityStateImpl stateImpl = (CodeTraceabilityStateImpl) codeTraceabilityState;
                stateImpl.setUseLiveLLM(this.useLiveLLM);
                stateImpl.setCurrentRun(this.currentRun);
                stateImpl.setCurrentModel(this.currentModel);
                stateImpl.setCurrentProject(this.currentProject);
            }
            
            dataRepository.addData(CodeTraceabilityState.ID, codeTraceabilityState);
            logger.info("CodeTraceabilityState created and configured for SAD-Code");
        } else {
            // ADDED: Update existing state with current run information
            CodeTraceabilityState existingState = DataRepositoryHelper.getCodeTraceabilityState(dataRepository);
            if (existingState instanceof CodeTraceabilityStateImpl) {
                CodeTraceabilityStateImpl stateImpl = (CodeTraceabilityStateImpl) existingState;
                stateImpl.setUseLiveLLM(this.useLiveLLM);
                stateImpl.setCurrentRun(this.currentRun);
                stateImpl.setCurrentModel(this.currentModel);
                stateImpl.setCurrentProject(this.currentProject);
                logger.info("Updated existing CodeTraceabilityState for run {}", this.currentRun);
            }
        }
    }

    // ADDED: Getter methods for configuration
    public boolean isUseLiveLLM() {
        return useLiveLLM;
    }

    public int getCurrentRun() {
        return currentRun;
    }

    public String getCurrentModel() {
        return currentModel;
    }

    public String getCurrentProject() {
        return currentProject;
    }

    // ADDED: Method to run with statistics collection
    public void runWithStatistics() {
        logger.info("=== Starting SAD-Code Traceability Recovery ===");
        logger.info("Run: {}, Model: {}, Project: {}", currentRun, currentModel, currentProject);
        
        long startTime = System.currentTimeMillis();
        
        // Run the actual pipeline
        run();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Log completion
        logger.info("=== Completed SAD-Code Traceability Recovery ===");
        logger.info("Duration: {} ms", duration);
        
        // Print statistics from state
        DataRepository dataRepository = getDataRepository();
        if (DataRepositoryHelper.hasCodeTraceabilityState(dataRepository)) {
            CodeTraceabilityState state = DataRepositoryHelper.getCodeTraceabilityState(dataRepository);
            if (state instanceof CodeTraceabilityStateImpl) {
                ((CodeTraceabilityStateImpl) state).complete();
            }
        }
    }

    // ADDED: Reset method for multiple runs
    public void resetState() {
        DataRepository dataRepository = getDataRepository();
        if (DataRepositoryHelper.hasCodeTraceabilityState(dataRepository)) {
            CodeTraceabilityState state = DataRepositoryHelper.getCodeTraceabilityState(dataRepository);
            if (state instanceof CodeTraceabilityStateImpl) {
                ((CodeTraceabilityStateImpl) state).reset();
                logger.info("CodeTraceabilityState reset for run {}", currentRun);
            }
        }
    }
}
