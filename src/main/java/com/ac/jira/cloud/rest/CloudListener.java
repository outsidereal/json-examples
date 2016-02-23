package com.ac.jira.cloud.rest;

import com.ac.jira.cloud.CloudIssueSynchronizer;
import com.ac.jira.cloud.rest.bean.CommentEventBean;
import com.ac.jira.cloud.rest.bean.IssueEventBean;
import com.ac.jira.cloud.rest.bean.WorklogEventBean;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.rest.v2.issue.IssueBean;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import org.apache.log4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.atlassian.jira.util.ImportUtils.isIndexIssues;
import static com.atlassian.jira.util.ImportUtils.setIndexIssues;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;

@Path("/project/{projectKey}")
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
@AnonymousAllowed
public class CloudListener {
    private static final Logger LOG = Logger.getLogger(CloudListener.class);
    private static final MutableIssue ISSUE_TO_CLONE = ComponentAccessor.getIssueManager().getIssueObject("SAP-27606");
    private CloudIssueSynchronizer synchronizer;

    public CloudListener() {
        this.synchronizer = ComponentAccessor.getComponentOfType(CloudIssueSynchronizer.class);
    }

    @POST
    @Path("/issue/{issueKey}/create")
    public Response issueCreated(@PathParam("projectKey") String projectKey,
                                 @PathParam("issueKey") String issueKey,
                                 IssueEventBean issueCreated) {
        Project project = getProjectByExternalProjectKey(projectKey);
        ApplicationUser user = getClientUserByProjectKey(projectKey);

        createInternalIssue(issueCreated.getIssue(), project, user);

        logEvent(issueCreated);
        return buildOKResponseIfEventNotNull(issueCreated);
    }

    private Project getProjectByExternalProjectKey(String projectKey) {
        return ComponentAccessor.getProjectManager().getProjectObjByKey("SAP");
    }

    private ApplicationUser getClientUserByProjectKey(String projectKey) {
        return ComponentAccessor.getUserManager().getUserByName("dm.ulanovych");
    }

    private void createInternalIssue(IssueBean issueBean, Project project, ApplicationUser user) {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);

        synchronizer.createInternalIssue(user, ISSUE_TO_CLONE, project);

        setIndexIssues(oldIndexIssuesValue);
    }

    @POST
    @Path("/issue/{issueKey}/update")
    public Response issueUpdated(@PathParam("issueKey") String issueKey,
                                 IssueEventBean issueUpdated) {
        logEvent(issueUpdated);
        return buildOKResponseIfEventNotNull(issueUpdated);
    }

    @POST
    @Path("/issue/{issueKey}/delete")
    public Response issueDeleted(@PathParam("issueKey") String issueKey,
                                 String issueDeleted) {
        logEvent(issueDeleted);
        return buildOKResponseIfEventNotNull(issueDeleted);
    }

    @POST
    @Path("/issue/{issueKey}/comment/{commentId}/create")
    public Response createComment(@PathParam("issueKey") String issueKey,
                                  @PathParam("commentId") String commentId,
                                  CommentEventBean commentCreated) {

        logEvent(commentCreated);
        return buildOKResponseIfEventNotNull(commentCreated);
    }

    @POST
    @Path("/issue/{issueKey}/comment/{commentId}/update")
    public Response updateComment(@PathParam("issueKey") String issueKey,
                                  @PathParam("commentId") String commentId,
                                  CommentEventBean commentUpdated) {
        logEvent(commentUpdated);
        return buildOKResponseIfEventNotNull(commentUpdated);
    }

    @POST
    @Path("/issue/{issueKey}/comment/{commentId}/delete")
    public Response deleteComment(@PathParam("issueKey") String issueKey,
                                  @PathParam("commentId") String commentId,
                                  CommentEventBean commentDeleted) {
        logEvent(commentDeleted);
        return buildOKResponseIfEventNotNull(commentDeleted);
    }

    @POST
    @Path("/worklog/create")
    public Response worklogCreated(WorklogEventBean worklogCreated) {
        logEvent(worklogCreated);
        return buildOKResponseIfEventNotNull(worklogCreated);
    }

    @POST
    @Path("/worklog/update")
    public Response worklogUpdated(WorklogEventBean worklogUpdated) {
        logEvent(worklogUpdated);
        return buildOKResponseIfEventNotNull(worklogUpdated);
    }

    @POST
    @Path("/worklog/delete")
    public Response worklogDeleted(WorklogEventBean worklogDeleted) {
        logEvent(worklogDeleted);
        return buildOKResponseIfEventNotNull(worklogDeleted);
    }

    @POST
    @Path("/version/{versionId}")
    public Response versionEvent(@PathParam("projectKey") String projectKey,
                                 @PathParam("versionId") String versionId,
                                 String versionEventBean) {
        logEvent(versionEventBean);
        return ok().build();
    }

    private Response buildOKResponseIfEventNotNull(Object event) {
        return event == null ? status(BAD_REQUEST).build() : ok().build();
    }

    private void logEvent(Object eventBean) {
        LOG.warn(eventBean);
    }
}
