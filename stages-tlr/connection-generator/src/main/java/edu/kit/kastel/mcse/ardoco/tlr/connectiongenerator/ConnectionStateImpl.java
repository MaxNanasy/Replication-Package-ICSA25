/* Licensed under MIT 2026-2027. */
package edu.kit.kastel.mcse.ardoco.tlr.connectiongenerator;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.core.api.connectiongenerator.ConnectionState;
import edu.kit.kastel.mcse.ardoco.core.api.models.ModelInstance;
import edu.kit.kastel.mcse.ardoco.core.api.models.tracelinks.InstanceLink;
import edu.kit.kastel.mcse.ardoco.core.api.recommendationgenerator.RecommendedInstance;
import edu.kit.kastel.mcse.ardoco.core.data.AbstractState;
import edu.kit.kastel.mcse.ardoco.core.pipeline.agent.Claimant;

/**
 * The connection state encapsulates all connections between the model extraction state and the recommendation state.
 * These connections are stored in instance and relation links.
 * 
 * MODIFIED: Added replication support, statistics tracking, and detailed logging.
 */
public class ConnectionStateImpl extends AbstractState implements ConnectionState {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionStateImpl.class);

    private MutableList<InstanceLink> instanceLinks;
    
    // ADDED: Statistics tracking fields
    private int totalAddAttempts = 0;
    private int successfulAdds = 0;
    private int duplicateAttempts = 0;
    private int removeCount = 0;
    private long creationTime;
    private long lastModificationTime;
    
    // ADDED: Configuration tracking
    private boolean useLiveLLM = false;
    private int currentRun = 1;
    private String currentProject = "unknown";
    private String currentModel = "unknown";

    /**
     * Creates a new connection state.
     */
    public ConnectionStateImpl() {
        super();
        instanceLinks = Lists.mutable.empty();
        creationTime = System.currentTimeMillis();
        lastModificationTime = creationTime;
        logger.debug("ConnectionStateImpl created at timestamp: {}", creationTime);
    }
    
    // ADDED: Configuration setter methods
    public void setUseLiveLLM(boolean useLiveLLM) {
        this.useLiveLLM = useLiveLLM;
    }
    
    public void setCurrentRun(int currentRun) {
        this.currentRun = currentRun;
    }
    
    public void setCurrentProject(String currentProject) {
        this.currentProject = currentProject;
    }
    
    public void setCurrentModel(String currentModel) {
        this.currentModel = currentModel;
    }

    /**
     * Returns all instance links.
     *
     * @return all instance links
     */
    @Override
    public ImmutableList<InstanceLink> getInstanceLinks() {
        return Lists.immutable.withAll(instanceLinks);
    }

    /**
     * Returns all instance links with a model instance containing the given name.
     *
     * @param name the name of a model instance
     * @return all instance links with a model instance containing the given name as list
     */
    @Override
    public ImmutableList<InstanceLink> getInstanceLinksByName(String name) {
        return Lists.immutable.fromStream(instanceLinks.stream().filter(imapping -> imapping.getModelInstance().getNameParts().contains(name)));
    }

    /**
     * Returns all instance links with a model instance containing the given type.
     *
     * @param type the type of a model instance
     * @return all instance links with a model instance containing the given type as list
     */
    @Override
    public ImmutableList<InstanceLink> getInstanceLinksByType(String type) {
        return Lists.immutable.fromStream(instanceLinks.stream().filter(ilink -> ilink.getModelInstance().getTypeParts().contains(type)));
    }

    @Override
    public ImmutableList<InstanceLink> getInstanceLinksByRecommendedInstance(RecommendedInstance recommendedInstance) {
        return Lists.immutable.fromStream(instanceLinks.stream().filter(il -> il.getTextualInstance().equals(recommendedInstance)));
    }

    /**
     * Returns all instance links with a model instance containing the given name and type.
     *
     * @param type the type of a model instance
     * @param name the name of a model instance
     * @return all instance links with a model instance containing the given name and type as list
     */
    @Override
    public ImmutableList<InstanceLink> getInstanceLinks(String name, String type) {
        return Lists.immutable.fromStream(instanceLinks.stream()
                .filter(imapping -> imapping.getModelInstance().getNameParts().contains(name))//
                .filter(imapping -> imapping.getModelInstance().getTypeParts().contains(type)));
    }

    /**
     * Adds the connection of a recommended instance and a model instance to the state. If the model instance is already
     * contained by the state it is extended. Elsewhere a new instance link is created
     *
     * @param recommendedModelInstance the recommended instance
     * @param instance                 the model instance
     * @param probability              the probability of the link
     */
    @Override
    public void addToLinks(RecommendedInstance recommendedModelInstance, ModelInstance instance, Claimant claimant, double probability) {
        totalAddAttempts++;
        
        var newInstanceLink = new InstanceLink(recommendedModelInstance, instance, claimant, probability);
        if (!isContainedByInstanceLinks(newInstanceLink)) {
            instanceLinks.add(newInstanceLink);
            successfulAdds++;
            lastModificationTime = System.currentTimeMillis();
            
            if (useLiveLLM) {
                logger.debug("Run {}: Added new instance link for project {} - Model: {}, Instance: {}", 
                    currentRun, currentProject, currentModel, instance.getName());
            }
        } else {
            duplicateAttempts++;
            var optionalInstanceLink = instanceLinks.stream().filter(il -> il.equals(newInstanceLink)).findFirst();
            if (optionalInstanceLink.isPresent()) {
                var existingInstanceLink = optionalInstanceLink.get();
                var newNameMappings = newInstanceLink.getTextualInstance().getNameMappings();
                var newTypeMappings = newInstanceLink.getTextualInstance().getTypeMappings();
                existingInstanceLink.getTextualInstance().addMappings(newNameMappings, newTypeMappings);
                lastModificationTime = System.currentTimeMillis();
                
                if (useLiveLLM) {
                    logger.debug("Run {}: Extended existing instance link for project {} - Model: {}", 
                        currentRun, currentProject, currentModel);
                }
            }
        }
    }

    /**
     * Checks if an instance link is already contained by the state.
     *
     * @param instanceLink the given instance link
     * @return true if it is already contained
     */
    @Override
    public boolean isContainedByInstanceLinks(InstanceLink instanceLink) {
        return instanceLinks.contains(instanceLink);
    }

    /**
     * Removes an instance link from the state
     *
     * @param instanceMapping the instance link to remove
     */
    @Override
    public void removeFromMappings(InstanceLink instanceMapping) {
        instanceLinks.remove(instanceMapping);
        removeCount++;
        lastModificationTime = System.currentTimeMillis();
        logger.debug("Removed instance link - Total removals: {}", removeCount);
    }

    /**
     * Removes all instance links containing the given instance
     *
     * @param instance the given instance
     */
    @Override
    public void removeAllInstanceLinksWith(ModelInstance instance) {
        int removedCount = instanceLinks.count(mapping -> mapping.getModelInstance().equals(instance));
        instanceLinks.removeIf(mapping -> mapping.getModelInstance().equals(instance));
        removeCount += removedCount;
        lastModificationTime = System.currentTimeMillis();
        logger.debug("Removed {} instance links containing model instance", removedCount);
    }

    /**
     * Removes all instance links containing the given recommended instance
     *
     * @param instance the given recommended instance
     */
    @Override
    public void removeAllInstanceLinksWith(RecommendedInstance instance) {
        int removedCount = instanceLinks.count(mapping -> mapping.getTextualInstance().equals(instance));
        instanceLinks.removeIf(mapping -> mapping.getTextualInstance().equals(instance));
        removeCount += removedCount;
        lastModificationTime = System.currentTimeMillis();
        logger.debug("Removed {} instance links containing recommended instance", removedCount);
    }
    
    // ADDED: Statistics and utility methods
    
    /**
     * Gets the total number of instance links.
     */
    public int getInstanceLinksCount() {
        return instanceLinks.size();
    }
    
    /**
     * Gets the total number of add attempts.
     */
    public int getTotalAddAttempts() {
        return totalAddAttempts;
    }
    
    /**
     * Gets the number of successful adds.
     */
    public int getSuccessfulAdds() {
        return successfulAdds;
    }
    
    /**
     * Gets the number of duplicate attempts.
     */
    public int getDuplicateAttempts() {
        return duplicateAttempts;
    }
    
    /**
     * Gets the number of removals.
     */
    public int getRemoveCount() {
        return removeCount;
    }
    
    /**
     * Gets the creation timestamp.
     */
    public long getCreationTime() {
        return creationTime;
    }
    
    /**
     * Gets the last modification timestamp.
     */
    public long getLastModificationTime() {
        return lastModificationTime;
    }
    
    /**
     * Gets the age of the state in milliseconds.
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Gets the time since last modification in milliseconds.
     */
    public long getTimeSinceLastModificationMs() {
        return System.currentTimeMillis() - lastModificationTime;
    }
    
    /**
     * Resets all statistics for a new run.
     */
    public void resetStatistics() {
        this.totalAddAttempts = 0;
        this.successfulAdds = 0;
        this.duplicateAttempts = 0;
        this.removeCount = 0;
        this.creationTime = System.currentTimeMillis();
        this.lastModificationTime = creationTime;
        logger.info("ConnectionStateImpl statistics reset for run {}", currentRun);
    }
    
    /**
     * Clears all instance links and resets statistics.
     */
    public void clear() {
        instanceLinks.clear();
        resetStatistics();
        logger.info("ConnectionStateImpl cleared for run {}", currentRun);
    }
    
    /**
     * Prints statistics to the log.
     */
    public void printStatistics() {
        logger.info("========================================");
        logger.info("ConnectionStateImpl Statistics");
        logger.info("========================================");
        logger.info("Project: {}", currentProject);
        logger.info("Model: {}", currentModel);
        logger.info("Run: {}", currentRun);
        logger.info("Live LLM Mode: {}", useLiveLLM);
        logger.info("----------------------------------------");
        logger.info("Current Instance Links: {}", instanceLinks.size());
        logger.info("Total Add Attempts: {}", totalAddAttempts);
        logger.info("Successful Adds: {}", successfulAdds);
        logger.info("Duplicate Attempts: {}", duplicateAttempts);
        logger.info("Total Removals: {}", removeCount);
        logger.info("----------------------------------------");
        logger.info("Age: {} ms", getAgeMs());
        logger.info("Time since last modification: {} ms", getTimeSinceLastModificationMs());
        logger.info("========================================");
    }
    
    /**
     * Returns statistics as a formatted string.
     */
    public String getStatisticsString() {
        return String.format(
            "ConnectionState Stats - Run: %d, Project: %s, Model: %s, Links: %d, Attempts: %d, Success: %d, Duplicates: %d, Removals: %d, Age: %d ms",
            currentRun, currentProject, currentModel, instanceLinks.size(), totalAddAttempts, successfulAdds, duplicateAttempts, removeCount, getAgeMs()
        );
    }
    
    /**
     * Returns statistics in CSV format.
     */
    public String toCSV() {
        return String.format("%d,%s,%s,%d,%d,%d,%d,%d,%d,%d",
            currentRun, currentProject, currentModel, instanceLinks.size(), totalAddAttempts, successfulAdds, duplicateAttempts, removeCount, 
            getAgeMs(), getTimeSinceLastModificationMs()
        );
    }
    
    /**
     * Gets the CSV header for statistics.
     */
    public static String getCSVHeader() {
        return "Run,Project,Model,InstanceLinksCount,TotalAddAttempts,SuccessfulAdds,DuplicateAttempts,Removals,AgeMs,TimeSinceLastModificationMs";
    }
}
