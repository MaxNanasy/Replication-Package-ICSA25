/* Licensed under MIT 2026-2027. */
package edu.kit.kastel.mcse.ardoco.tlr.codetraceability;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.factory.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.core.api.codetraceability.CodeTraceabilityState;
import edu.kit.kastel.mcse.ardoco.core.api.models.tracelinks.SadCodeTraceLink;
import edu.kit.kastel.mcse.ardoco.core.api.models.tracelinks.SamCodeTraceLink;
import edu.kit.kastel.mcse.ardoco.core.architecture.Deterministic;
import edu.kit.kastel.mcse.ardoco.core.data.AbstractState;

@Deterministic
public class CodeTraceabilityStateImpl extends AbstractState implements CodeTraceabilityState {

    private static final Logger logger = LoggerFactory.getLogger(CodeTraceabilityStateImpl.class);

    private MutableList<SamCodeTraceLink> samCodeTraceLinks = Lists.mutable.empty();
    private MutableList<SadCodeTraceLink> transitiveTraceLinks = Lists.mutable.empty();

    // ADDED: Statistics tracking fields
    private int totalSamTraceLinksAttempted = 0;
    private int successfulSamTraceLinks = 0;
    private int totalSadTraceLinksAttempted = 0;
    private int successfulSadTraceLinks = 0;
    private long startTime;
    private long endTime;

    // ADDED: Live LLM tracking fields
    private boolean useLiveLLM = false;
    private int currentRun = 1;
    private String currentModel = "unknown";
    private String currentProject = "unknown";

    public CodeTraceabilityStateImpl() {
        super();
        this.startTime = System.currentTimeMillis();
    }

    // ADDED: Configuration methods for live LLM tracking
    public void setUseLiveLLM(boolean useLiveLLM) {
        this.useLiveLLM = useLiveLLM;
        if (useLiveLLM) {
            logger.info("Live LLM mode enabled for traceability state");
        }
    }

    public void setCurrentRun(int currentRun) {
        this.currentRun = currentRun;
        logger.debug("Current run set to: {}", currentRun);
    }

    public void setCurrentModel(String model) {
        this.currentModel = model;
    }

    public void setCurrentProject(String project) {
        this.currentProject = project;
    }

    @Override
    public boolean addSamCodeTraceLink(SamCodeTraceLink traceLink) {
        totalSamTraceLinksAttempted++;
        boolean result = this.samCodeTraceLinks.add(traceLink);
        if (result) {
            successfulSamTraceLinks++;
        }
        
        // ADDED: Logging for live LLM mode
        if (useLiveLLM) {
            logger.debug("Run {}: SAM-Code trace link added for model {} - Project: {}, Target: {}", 
                currentRun, currentModel, currentProject, traceLink.getCodeArtifact());
        }
        return result;
    }

    @Override
    public boolean addSamCodeTraceLinks(Collection<SamCodeTraceLink> traceLinks) {
        int addedCount = 0;
        for (SamCodeTraceLink link : traceLinks) {
            totalSamTraceLinksAttempted++;
            if (this.samCodeTraceLinks.add(link)) {
                addedCount++;
            }
        }
        successfulSamTraceLinks += addedCount;
        
        // ADDED: Summary logging
        if (useLiveLLM) {
            logger.info("Run {}: Added {}/{} SAM-Code trace links for model {}", 
                currentRun, addedCount, traceLinks.size(), currentModel);
        }
        return addedCount > 0;
    }

    @Override
    public ImmutableSet<SamCodeTraceLink> getSamCodeTraceLinks() {
        return Sets.immutable.withAll(new LinkedHashSet<>(this.samCodeTraceLinks));
    }

    @Override
    public boolean addSadCodeTraceLink(SadCodeTraceLink traceLink) {
        totalSadTraceLinksAttempted++;
        boolean result = this.transitiveTraceLinks.add(traceLink);
        if (result) {
            successfulSadTraceLinks++;
        }
        
        // ADDED: Logging for live LLM mode
        if (useLiveLLM) {
            logger.debug("Run {}: SAD-Code transitive trace link added for model {} - Target: {}", 
                currentRun, currentModel, traceLink.getCodeArtifact());
        }
        return result;
    }

    @Override
    public boolean addSadCodeTraceLinks(Collection<SadCodeTraceLink> traceLinks) {
        int addedCount = 0;
        for (SadCodeTraceLink link : traceLinks) {
            totalSadTraceLinksAttempted++;
            if (this.transitiveTraceLinks.add(link)) {
                addedCount++;
            }
        }
        successfulSadTraceLinks += addedCount;
        
        // ADDED: Summary logging
        if (useLiveLLM) {
            logger.info("Run {}: Added {}/{} SAD-Code transitive trace links for model {}", 
                currentRun, addedCount, traceLinks.size(), currentModel);
        }
        return addedCount > 0;
    }

    @Override
    public ImmutableSet<SadCodeTraceLink> getSadCodeTraceLinks() {
        return this.transitiveTraceLinks.toImmutableSet();
    }

    // ADDED: Statistics methods
    public void complete() {
        this.endTime = System.currentTimeMillis();
        printStatistics();
    }

    public long getDurationMs() {
        return endTime - startTime;
    }

    public int getTotalSamTraceLinksAttempted() {
        return totalSamTraceLinksAttempted;
    }

    public int getSuccessfulSamTraceLinks() {
        return successfulSamTraceLinks;
    }

    public int getTotalSadTraceLinksAttempted() {
        return totalSadTraceLinksAttempted;
    }

    public int getSuccessfulSadTraceLinks() {
        return successfulSadTraceLinks;
    }

    public double getSamSuccessRate() {
        if (totalSamTraceLinksAttempted == 0) return 0.0;
        return (double) successfulSamTraceLinks / totalSamTraceLinksAttempted;
    }

    public double getSadSuccessRate() {
        if (totalSadTraceLinksAttempted == 0) return 0.0;
        return (double) successfulSadTraceLinks / totalSadTraceLinksAttempted;
    }

    public void printStatistics() {
        logger.info("========================================");
        logger.info("CODE TRACEABILITY STATISTICS SUMMARY");
        logger.info("========================================");
        logger.info("Project: {}", currentProject);
        logger.info("Model: {}", currentModel);
        logger.info("Run: {}", currentRun);
        logger.info("Live LLM Mode: {}", useLiveLLM);
        logger.info("----------------------------------------");
        logger.info("SAM-Code Trace Links:");
        logger.info("  Attempted: {}", totalSamTraceLinksAttempted);
        logger.info("  Successful: {}", successfulSamTraceLinks);
        logger.info("  Success Rate: {:.2f}%", getSamSuccessRate() * 100);
        logger.info("----------------------------------------");
        logger.info("SAD-Code (Transitive) Trace Links:");
        logger.info("  Attempted: {}", totalSadTraceLinksAttempted);
        logger.info("  Successful: {}", successfulSadTraceLinks);
        logger.info("  Success Rate: {:.2f}%", getSadSuccessRate() * 100);
        logger.info("----------------------------------------");
        logger.info("Total Duration: {} ms", getDurationMs());
        logger.info("========================================");
    }

    // ADDED: Reset method for multiple runs
    public void reset() {
        this.samCodeTraceLinks = Lists.mutable.empty();
        this.transitiveTraceLinks = Lists.mutable.empty();
        this.totalSamTraceLinksAttempted = 0;
        this.successfulSamTraceLinks = 0;
        this.totalSadTraceLinksAttempted = 0;
        this.successfulSadTraceLinks = 0;
        this.startTime = System.currentTimeMillis();
        logger.info("CodeTraceabilityState reset for run {}", currentRun);
    }

    // ADDED: Export results as CSV string
    public String toCSV() {
        return String.format("%s,%s,%d,%d,%.4f,%d,%d,%.4f,%d",
            currentProject,
            currentModel,
            totalSamTraceLinksAttempted,
            successfulSamTraceLinks,
            getSamSuccessRate(),
            totalSadTraceLinksAttempted,
            successfulSadTraceLinks,
            getSadSuccessRate(),
            getDurationMs()
        );
    }

    // ADDED: Get CSV header
    public static String getCSVHeader() {
        return "Project,Model,SamAttempted,SamSuccessful,SamSuccessRate,SadAttempted,SadSuccessful,SadSuccessRate,DurationMs";
    }
}
