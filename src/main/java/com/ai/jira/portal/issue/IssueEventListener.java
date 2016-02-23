package com.ai.jira.portal.issue;

import com.ai.jira.portal.ao.project.ProjectExtraFields;
import com.ai.jira.portal.ao.project.ProjectExtraFieldsService;
import com.atlassian.event.api.EventListener;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.MutableComment;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogImpl;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.ApplicationUsers;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Collection;

import static com.ai.jira.portal.issue.IssueSynchronizer.PORTAL_TAG;
import static com.ai.jira.project.comment.CommentViewIssueContextProvider.CLIENT_PROJECT_ROLE;
import static com.atlassian.jira.event.type.EventType.*;
import static com.atlassian.jira.util.ImportUtils.isIndexIssues;
import static com.atlassian.jira.util.ImportUtils.setIndexIssues;

public class IssueEventListener {
    private static final Logger LOG = Logger.getLogger(IssueEventListener.class);
    private static final String INTERNAL_PROJECT_ROLE = "Internal Users";
    private static final IssueManager issueManager = ComponentAccessor.getIssueManager();
    private final ProjectExtraFieldsService extraFieldsService;
    private final IssueSynchronizer synchronizer;

    public IssueEventListener(ProjectExtraFieldsService extraFieldsService, IssueSynchronizer synchronizer) {
        this.extraFieldsService = extraFieldsService;
        this.synchronizer = synchronizer;
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        Long eventTypeId = issueEvent.getEventTypeId();
        MutableIssue issue = ISSUE_DELETED_ID.equals(eventTypeId) ? (MutableIssue) issueEvent.getIssue() : issueManager.getIssueObject(issueEvent.getIssue().getId());
        Project project = issue.getProjectObject();
        Comment comment = issueEvent.getComment();
        ProjectExtraFields projectExtra = extraFieldsService.getProjectExtra(project.getId());
        Project relatedProject = ComponentAccessor.getProjectManager().getProjectObj(projectExtra.getRelatedProjectId());
        ApplicationUser user = ApplicationUsers.from(issueEvent.getUser());

        if (shouldModifyOneProjectPortalComment(eventTypeId, issue, comment, projectExtra)) {
            modifyOneProjectPortalBunchComment((MutableComment) comment);
        }

        if (ISSUE_CREATED_ID.equals(eventTypeId) && shouldCreateMirrorIssue(projectExtra, issue)) {
            processIssueCreatedEvent(issue, user, relatedProject, projectExtra);
        } else if (ISSUE_UPDATED_ID.equals(eventTypeId)) {
            processIssueUpdatedEvent(issue, user, relatedProject, comment, projectExtra);
        } else if (ISSUE_COMMENTED_ID.equals(eventTypeId)) {
            processIssueCommentedEvent(issue, comment, projectExtra);
        } else if (isCustomCreatedEvent(eventTypeId)) {
            processCustomEvent(issue, user, relatedProject, projectExtra, comment, eventTypeId);
        } else if (isBasicIssueTransitionEvent(eventTypeId)) {
            processBasicIssueTransitionEvent(issue, user, relatedProject, projectExtra, issueEvent);
        } else if (ISSUE_ASSIGNED_ID.equals(eventTypeId)) {
            processIssueAssignedEvent(issue, user, relatedProject, projectExtra, comment);
        } else if (ISSUE_COMMENT_EDITED_ID.equals(eventTypeId)) {
            processCommentEditedEvent(issue, comment, projectExtra.isPortal());
        } else if (ISSUE_DELETED_ID.equals(eventTypeId)) {
            processIssueDeletedEvent(issue, relatedProject, projectExtra);
        } else if (ISSUE_WORKLOGGED_ID.equals(eventTypeId)) {
            processWorklogEvent(issueEvent.getWorklog(), user);
        }
    }

    private void modifyOneProjectPortalBunchComment(MutableComment comment) {
        ProjectRoleManager roleManager = ComponentAccessor.getComponentOfType(ProjectRoleManager.class);
        String commentBody = comment.getBody();
        if (shouldBeVisibleOnlyForInternal(comment, roleManager, commentBody)) {
            ProjectRole internalUsers = roleManager.getProjectRole(INTERNAL_PROJECT_ROLE);
            comment.setRoleLevelId(internalUsers.getId());
            ComponentAccessor.getCommentManager().update(comment, false);
        } else if (commentBody.startsWith(PORTAL_TAG)) {
            comment.setBody(commentBody.replaceFirst(PORTAL_TAG, ""));
            ComponentAccessor.getCommentManager().update(comment, false);
        }
    }

    private boolean shouldBeVisibleOnlyForInternal(MutableComment comment, ProjectRoleManager roleManager, String commentBody) {
        return !commentBody.startsWith(PORTAL_TAG) &&
                !roleManager.isUserInProjectRole(comment.getAuthorApplicationUser(), roleManager.getProjectRole(CLIENT_PROJECT_ROLE), comment.getIssue().getProjectObject());
    }

    private boolean shouldModifyOneProjectPortalComment(Long eventTypeId, MutableIssue issue, Comment comment, ProjectExtraFields projectExtra) {
        return projectExtra.isOneProjectPortal() &&
                !ISSUE_COMMENT_EDITED_ID.equals(eventTypeId) &&
                !issue.isSubTask() &&
                comment instanceof MutableComment;
    }

    private boolean isBasicIssueTransitionEvent(Long eventTypeId) {
        return ISSUE_RESOLVED_ID.equals(eventTypeId) ||
                ISSUE_REOPENED_ID.equals(eventTypeId) ||
                ISSUE_CLOSED_ID.equals(eventTypeId) ||
                ISSUE_WORKSTARTED_ID.equals(eventTypeId) ||
                ISSUE_WORKSTOPPED_ID.equals(eventTypeId);
    }

    private boolean isCustomCreatedEvent(Long eventTypeId) {
        return eventTypeId >= 10000;
    }

    private void processIssueCreatedEvent(MutableIssue issue, ApplicationUser user, Project relatedProject, ProjectExtraFields projectExtra) {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);

        if (synchronizer.isProcess(issue, ISSUE_CREATED_ID) && projectExtra.isBidirectional() && null != relatedProject) {
            if (projectExtra.isPortal() || (!projectExtra.isPortal() && !issue.getIssueTypeObject().isSubTask())) {
                synchronizer.createMirrorIssue(user, issue, relatedProject);
            }
        }

        setIndexIssues(oldIndexIssuesValue);
    }

    private void processIssueUpdatedEvent(MutableIssue issue, ApplicationUser user, Project relatedProject, Comment comment, ProjectExtraFields projectExtra) {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);

        if (null != relatedProject) {
            MutableIssue relatedIssue = synchronizer.getRelatedIssue(projectExtra.isPortal(), issue);
            if (null != relatedIssue) {
                if (projectExtra.isPortal() || !issue.getIssueTypeObject().isSubTask()) {
                    synchronizer.updateIssue(user, issue, relatedIssue, comment, ISSUE_UPDATED_ID);
                }
            } else {
                LOG.warn("Related Issue doesn't exist for " + issue);
            }
        }

        setIndexIssues(oldIndexIssuesValue);
    }

    private void processIssueCommentedEvent(MutableIssue issue, Comment comment, ProjectExtraFields projectExtra) {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);

        if (synchronizer.isProcess(issue, ISSUE_COMMENTED_ID) && (projectExtra.isPortal() || synchronizer.shouldNotifyPortal(comment))) {
            MutableIssue relatedIssue = synchronizer.getRelatedIssue(projectExtra.isPortal(), issue);
            if (null != relatedIssue) {
                synchronizer.copyComment(issue, relatedIssue, comment);
            } else {
                LOG.warn("Related Issue doesn't exist for " + issue);
            }
        }

        setIndexIssues(oldIndexIssuesValue);
    }

    private void processBasicIssueTransitionEvent(MutableIssue issue, ApplicationUser user, Project relatedProject, ProjectExtraFields projectExtra, IssueEvent event) {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);

        if (synchronizer.isProcess(issue, event.getEventTypeId()) && null != relatedProject) {
            MutableIssue relatedIssue = synchronizer.getRelatedIssue(projectExtra.isPortal(), issue);
            if (null != relatedIssue) {
                synchronizer.transitIssue(event.getEventTypeId(), issue, relatedIssue, user, event.getComment(), projectExtra);
            } else {
                LOG.warn("Related Issue doesn't exist for " + issue);
            }
        }
        setIndexIssues(oldIndexIssuesValue);
    }

    private void processCustomEvent(MutableIssue issue, ApplicationUser user, Project relatedProject, ProjectExtraFields projectExtra, Comment comment, Long eventTypeId) {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);

        if (synchronizer.isProcess(issue, eventTypeId) && null != relatedProject) {
            MutableIssue relatedIssue = synchronizer.getRelatedIssue(projectExtra.isPortal(), issue);
            if (null != relatedIssue) {
                if (projectExtra.isPortal() || !issue.getIssueTypeObject().isSubTask()) {
                    synchronizer.updateIssue(user, issue, relatedIssue, comment, eventTypeId);
                }
            } else {
                LOG.warn("Related Issue doesn't exist for " + issue);
            }
        }

        setIndexIssues(oldIndexIssuesValue);
    }

    private void processIssueAssignedEvent(MutableIssue issue, ApplicationUser user, Project relatedProject, ProjectExtraFields projectExtra, Comment comment) {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);

        if (synchronizer.isProcess(issue, ISSUE_ASSIGNED_ID) && null != relatedProject) {
            MutableIssue relatedIssue = synchronizer.getRelatedIssue(projectExtra.isPortal(), issue);
            if (null != relatedIssue) {
                synchronizer.assignee(issue, relatedIssue, comment, user);
            } else {
                LOG.warn("Related Issue doesn't exist for " + issue);
            }
        }

        setIndexIssues(oldIndexIssuesValue);
    }

    private void processIssueDeletedEvent(MutableIssue issue, Project relatedProject, ProjectExtraFields projectExtra) {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);

        if (null != relatedProject) {
            MutableIssue relatedIssue = synchronizer.getRelatedIssue(projectExtra.isPortal(), issue);
            if (relatedIssue != null) {
                if (projectExtra.isPortal()) {
                    synchronizer.removeIssueLink(issue);
                } else {
                    synchronizer.removeIssueLink(relatedIssue);
                }
                synchronizer.deleteIssue(relatedIssue);
            } else {
                LOG.warn("Related Issue doesn't exist for " + issue);
            }
        }

        setIndexIssues(oldIndexIssuesValue);
    }

    private void processCommentEditedEvent(MutableIssue issue, Comment comment, boolean isPortalProject) {
        if (synchronizer.isProcess(issue, ISSUE_COMMENT_EDITED_ID)) {
            MutableIssue relatedIssue = synchronizer.getRelatedIssue(isPortalProject, issue);
            if (null != relatedIssue) {
                synchronizer.updateComment(relatedIssue, comment);
            }
        }
    }

    private void processWorklogEvent(Worklog worklog, ApplicationUser user) {
        if (worklog.getComment().startsWith(PORTAL_TAG)) {
            Worklog updatedWorkLog = new WorklogImpl(ComponentAccessor.getWorklogManager(), worklog.getIssue(),
                    worklog.getId(), worklog.getAuthorKey(), worklog.getComment().replaceFirst(PORTAL_TAG, ""), worklog.getStartDate(),
                    worklog.getGroupLevel(), worklog.getRoleLevelId(), worklog.getTimeSpent());
            ComponentAccessor.getWorklogManager().update(user.getDirectoryUser(), updatedWorkLog, null, false);
        }
    }

    private boolean shouldCreateMirrorIssue(ProjectExtraFields projectExtra, Issue issue) {
        boolean shouldCreate = true;
        String notMappedIssueTypes = projectExtra.getNotMappedIssueTypeIds();
        if (null != notMappedIssueTypes && !projectExtra.isPortal()) {
            Collection<String> notMappedIssueTypeIds = Arrays.asList(notMappedIssueTypes.split(" "));
            shouldCreate = !notMappedIssueTypeIds.contains(issue.getIssueTypeId());
        }

        return shouldCreate;
    }
}
