package io.jenkins.plugins.gamekins.property;

import hudson.model.*;
import io.jenkins.plugins.gamekins.LeaderboardAction;
import io.jenkins.plugins.gamekins.StatisticsAction;
import io.jenkins.plugins.gamekins.statistics.Statistics;
import io.jenkins.plugins.gamekins.util.PropertyUtil;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

public class GameJobProperty extends hudson.model.JobProperty<Job<?, ?>> implements GameProperty {

    private boolean activated;
    private boolean showStatistics;
    private final ArrayList<String> teams;
    private Statistics statistics;

    @DataBoundConstructor
    public GameJobProperty(AbstractItem job, boolean activated, boolean showStatistics) {
        this.activated = activated;
        this.showStatistics = showStatistics;
        this.teams = new ArrayList<>();
        this.statistics = new Statistics(job);
    }

    public boolean getActivated() {
        return this.activated;
    }

    @DataBoundSetter
    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public boolean getShowStatistics() {
        return this.showStatistics;
    }

    @DataBoundSetter
    public void setShowStatistics(boolean showStatistics) {
        this.showStatistics = showStatistics;
    }

    public ArrayList<String> getTeams() {
        return this.teams;
    }

    @Override
    public Statistics getStatistics() {
        if (this.statistics == null || this.statistics.isNotFullyInitialized()) {
            this.statistics = new Statistics(this.owner);
        }
        return this.statistics;
    }

    @Override
    public AbstractItem getOwner() {
        return this.owner;
    }

    public void addTeam(String teamName) throws IOException {
        this.teams.add(teamName);
        owner.save();
    }

    public void removeTeam(String teamName) throws IOException {
        this.teams.remove(teamName);
        owner.save();
    }

    @Override
    public hudson.model.JobProperty<?> reconfigure(StaplerRequest req, JSONObject form) {
        if (form != null) this.activated = form.getBoolean("activated");
        if (form != null) this.showStatistics = form.getBoolean("showStatistics");
        PropertyUtil.reconfigure(owner, this.activated, this.showStatistics);
        return this;
    }

    /**
     * {@link Action}s to be displayed in the job page.
     *
     * <p>
     * Returning actions from this method allows a job property to add them
     * to the left navigation bar in the job page.
     *
     * <p>
     * {@link Action} can implement additional marker interface to integrate
     * with the UI in different ways.
     *
     * @param job Always the same as {@link #owner} but passed in anyway for backward compatibility (I guess.)
     *            You really need not use this value at all.
     * @return can be empty but never null.
     * @see ProminentProjectAction
     * @see PermalinkProjectAction
     * @since 1.341
     */
    @Nonnull
    @Override
    public Collection<? extends Action> getJobActions(Job<?, ?> job) {
        List<Action> newActions = new ArrayList<>();

        if (activated && (job.getAction(LeaderboardAction.class) == null || job instanceof FreeStyleProject)) {
            newActions.add(new LeaderboardAction(job));
        }
        if (showStatistics && (job.getAction(StatisticsAction.class) == null || job instanceof FreeStyleProject)) {
            newActions.add(new StatisticsAction(job));
        }

        return newActions;
    }
}
