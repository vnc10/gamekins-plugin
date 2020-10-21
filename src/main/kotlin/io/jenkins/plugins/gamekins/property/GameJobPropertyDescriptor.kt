package io.jenkins.plugins.gamekins.property

import hudson.Extension
import hudson.maven.AbstractMavenProject
import hudson.model.*
import hudson.util.FormValidation
import hudson.util.ListBoxModel
import io.jenkins.plugins.gamekins.util.PropertyUtil
import net.sf.json.JSONObject
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.kohsuke.stapler.AncestorInPath
import org.kohsuke.stapler.QueryParameter
import org.kohsuke.stapler.StaplerRequest
import javax.annotation.Nonnull

/**
 * Registers the [GameJobProperty] to Jenkins as an extension and also works as an communication point between the
 * Jetty server and the [GameJobProperty].
 *
 * @author Philipp Straubinger
 * @since 1.0
 */
@Extension
class GameJobPropertyDescriptor : JobPropertyDescriptor(GameJobProperty::class.java) {

    init {
        load()
    }

    /**
     * Called from the Jetty server if the button to add a new team is pressed. Only allows a non-empty [teamName] and
     * adds them to the [job], from which the button has been clicked, via the method [PropertyUtil.doAddTeam].
     */
    fun doAddTeam(@AncestorInPath job: Job<*, *>?, @QueryParameter teamName: String): FormValidation {
        if (teamName.isEmpty()) return FormValidation.error("Insert a name for the team")
        val property = if (job == null) null else job.getProperties()[this] as GameJobProperty?
        val validation = PropertyUtil.doAddTeam(property, teamName)
        save()
        return validation
    }

    /**
     * Called from the Jetty server if the button to add a new participant to a team is pressed. Adds the participant
     * [usersBox] to the team [teamsBox] via the method [PropertyUtil.doAddUserToTeam].
     */
    fun doAddUserToTeam(@AncestorInPath job: Job<*, *>?, @QueryParameter teamsBox: String?,
                        @QueryParameter usersBox: String?): FormValidation {
        return PropertyUtil.doAddUserToTeam(job, teamsBox!!, usersBox!!)
    }

    /**
     * Called from the Jetty server if the button to delete a team is pressed. Deletes the team [teamsBox] of the
     * [job] via the method [PropertyUtil.doDeleteTeam].
     */
    fun doDeleteTeam(@AncestorInPath job: Job<*, *>?, @QueryParameter teamsBox: String?): FormValidation {
        if (job == null) return FormValidation.error("Unexpected error: Parent job is null")
        val projectName = job.getName()
        val property = job.getProperties()[this] as GameJobProperty
        val validation = PropertyUtil.doDeleteTeam(projectName, property, teamsBox!!)
        save()
        return validation
    }

    /**
     * Called from the Jetty server when the configuration page is displayed. Fills the combo box with the names of
     * all teams of the [job].
     */
    fun doFillTeamsBoxItems(@AncestorInPath job: Job<*, *>?): ListBoxModel {
        val property = if (job == null) null else job.getProperties()[this] as GameJobProperty?
        return PropertyUtil.doFillTeamsBoxItems(property)
    }

    /**
     * Called from the Jetty server when the configuration page is displayed. Fills the combo box with the names of
     * all users of the [job].
     */
    fun doFillUsersBoxItems(@AncestorInPath job: Job<*, *>?): ListBoxModel {
        return if (job == null) ListBoxModel() else PropertyUtil.doFillUsersBoxItems(job.getName())
    }

    /**
     * Called from the Jetty server if the button to remove a participant from a team is pressed. Removes the
     * participant [usersBox] from the team [teamsBox] of the [job] via the method [PropertyUtil.doRemoveUserFromTeam].
     */
    fun doRemoveUserFromTeam(@AncestorInPath job: Job<*, *>?, @QueryParameter teamsBox: String?,
                             @QueryParameter usersBox: String?): FormValidation {
        return PropertyUtil.doRemoveUserFromTeam(job, teamsBox!!, usersBox!!)
    }

    @Nonnull
    override fun getDisplayName(): String {
        return "Set the activation of the Gamekins plugin."
    }

    /**
     * The [GameJobProperty] can only be added to jobs with the [jobType] [FreeStyleProject], [WorkflowJob] and
     * [AbstractMavenProject]. For other [jobType]s have a look at [GameMultiBranchProperty] and
     * [GameOrganizationFolderProperty].
     *
     * @see JobPropertyDescriptor.isApplicable
     */
    override fun isApplicable(jobType: Class<out Job<*, *>?>): Boolean {
        return jobType == FreeStyleProject::class.java || jobType == WorkflowJob::class.java
                || AbstractMavenProject::class.java.isAssignableFrom(jobType)
    }

    /**
     * Returns a new instance of a [GameJobProperty] during creation and saving of a job.
     *
     * @see JobPropertyDescriptor.newInstance
     */
    override fun newInstance(req: StaplerRequest?, formData: JSONObject): JobProperty<*>? {
        return if (req == null || req.findAncestor(AbstractItem::class.java).getObject() == null) null
        else GameJobProperty(req.findAncestor(AbstractItem::class.java).getObject() as AbstractItem,
                formData.getBoolean("activated"), formData.getBoolean("showStatistics"))
    }
}