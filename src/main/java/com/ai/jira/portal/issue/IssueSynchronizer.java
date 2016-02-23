package com.ai.jira.portal.issue;

import com.ai.jira.portal.ActionResolverManager;
import com.ai.jira.portal.ao.comment.CommentLink;
import com.ai.jira.portal.ao.comment.CommentLinkManager;
import com.ai.jira.portal.ao.issue.CustomIssueLink;
import com.ai.jira.portal.ao.issue.CustomIssueLinkManager;
import com.ai.jira.portal.ao.project.ProjectExtraFields;
import com.ai.jira.portal.ao.project.ProjectExtraFieldsService;
import com.ai.jira.portal.ao.version.VersionLinkManager;
import com.ai.jira.portal.mapping.PriorityMapper;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.issue.*;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.attachment.CreateAttachmentParamsBean;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.comments.MutableComment;
import com.atlassian.jira.issue.customfields.impl.CalculatedCFType;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.issue.index.IssueIndexManager;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleActors;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ImportUtils;
import com.atlassian.jira.web.util.AttachmentException;
import com.atlassian.jira.workflow.TransitionOptions;
import com.opensymphony.workflow.loader.ActionDescriptor;
import org.apache.log4j.Logger;

import java.io.File;
import java.sql.Timestamp;
import java.util.*;

import static com.atlassian.jira.event.type.EventDispatchOption.DO_NOT_DISPATCH;
import static com.atlassian.jira.util.AttachmentUtils.getAttachmentFile;

public class IssueSynchronizer {
    static final String PORTAL_TAG = "<portal/>";
    private static final Logger LOG = Logger.getLogger(IssueSynchronizer.class);
    private static final String AS_A_CLIENT = "As a Client";
    private static final String PORTAL_CASE_NUMBER = "Portal Key";
    private static final UpdateIssueRequest SILENT_ISSUE_UPDATE = UpdateIssueRequest.builder().sendMail(false).eventDispatchOption(DO_NOT_DISPATCH).build();
    private final Map<Long, List<String>> ignoreEventsFrom = new HashMap<>();
    private final ProjectRoleManager projectRoleManager;
    private final CustomIssueLinkManager customIssueLinkManager;
    private final VersionLinkManager versionLinkManager;
    private final ActionResolverManager actionResolverManager;
    private final CommentLinkManager commentLinkManager;
    private final ProjectExtraFieldsService extraFieldsService;
    private final IssueTransitionValidator issueTransitionValidator;
    private final IssueManager issueManager;

    protected IssueSynchronizer(ProjectRoleManager projectRoleManager, CustomIssueLinkManager customIssueLinkManager, VersionLinkManager versionLinkManager, ActionResolverManager actionResolverManager, CommentLinkManager commentLinkManager, ProjectExtraFieldsService extraFieldsService, IssueTransitionValidator issueTransitionValidator, IssueManager issueManager) {
        this.projectRoleManager = projectRoleManager;
        this.customIssueLinkManager = customIssueLinkManager;
        this.versionLinkManager = versionLinkManager;
        this.actionResolverManager = actionResolverManager;
        this.commentLinkManager = commentLinkManager;
        this.extraFieldsService = extraFieldsService;
        this.issueTransitionValidator = issueTransitionValidator;
        this.issueManager = issueManager;
    }


    /**
     * Resolves custom field definition, null if field with this name can't be resolved
     *
     * @param issue - which has expected field definition
     * @param name  - of field to resolve
     * @return CustomField
     */
    private static CustomField getCustomFieldByName(Issue issue, String name) {
        //Do not get watchers fields
        if (name.equalsIgnoreCase("watchers")) {
            return null;
        }
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        List<CustomField> customFields = customFieldManager.getCustomFieldObjects(issue.getProjectObject().getId(), issue.getIssueTypeObject().getId());
        for (CustomField customField : customFields) {
            if (customField.getNameKey().equals(name)) {
                return customField;
            }
        }
        return null;
    }

    boolean isProcess(Issue issue, Long eventTypeId) {
        boolean retVal = true;
        synchronized (ignoreEventsFrom) {
            if (ignoreEventsFrom.containsKey(eventTypeId)) {
                if (ignoreEventsFrom.get(eventTypeId).contains(Long.toString(issue.getId()))) {
                    ignoreEventsFrom.get(eventTypeId).remove(Long.toString(issue.getId()));
                    retVal = false;
                } else if (ignoreEventsFrom.get(eventTypeId).contains(issue.getSummary())) {
                    ignoreEventsFrom.get(eventTypeId).remove(issue.getSummary());
                    retVal = false;
                }
            }
        }
        return retVal;
    }

    private void doLock(Issue issue, Long eventTypeId) {
        synchronized (ignoreEventsFrom) {
            if (!ignoreEventsFrom.containsKey(eventTypeId)) {
                ignoreEventsFrom.put(eventTypeId, new ArrayList<String>());
            }
            if (!ignoreEventsFrom.get(eventTypeId).contains(Long.toString(issue.getId()))) {
                ignoreEventsFrom.get(eventTypeId).add(Long.toString(issue.getId()));
            }
        }
    }

    private void doLockByName(Issue issue, Long eventTypeId) {
        synchronized (ignoreEventsFrom) {
            if (!ignoreEventsFrom.containsKey(eventTypeId)) {
                ignoreEventsFrom.put(eventTypeId, new ArrayList<String>());
            }
            if (!ignoreEventsFrom.get(eventTypeId).contains(issue.getSummary())) {
                ignoreEventsFrom.get(eventTypeId).add(issue.getSummary());
            }
        }
    }

    void createMirrorIssue(ApplicationUser user, MutableIssue issue, Project relatedProject) {
        doLockByName(issue, EventType.ISSUE_CREATED_ID);

        IssueManager issueManager = ComponentAccessor.getIssueManager();
        MutableIssue mirrorIssue = cloneIssue(issue, relatedProject);
        User reporter = issue.getReporter();
        processComponents(issue, mirrorIssue, relatedProject);
        Boolean asAClient = false;

        mirrorIssue.setAssignee(issue.getAssignee());
        mirrorIssue.setReporter(reporter);

        if (extraFieldsService.getProjectExtra(relatedProject.getId()).isPortal()) {
            CustomField customField = getCustomFieldByName(issue, AS_A_CLIENT);
            if (null != customField) {
                @SuppressWarnings("unchecked")
                List<Option> options = (List<Option>) issue.getCustomFieldValue(customField);
                if (options != null && options.size() > 0) {
                    if (options.get(0).getValue().equals("Check if Yes")) {
                        reporter = getOwner(projectRoleManager, relatedProject);
                        asAClient = true;
                    }
                }
            }
        }

        Map<String, Object> context = new HashMap<>();
        context.put("issue", mirrorIssue);

        try {
            mirrorIssue = (MutableIssue) issueManager.createIssueObject(user.getDirectoryUser(), context);
            syncAttachments(issue, mirrorIssue, user);
            reindexIssue(mirrorIssue);

            if (extraFieldsService.getProjectExtra(issue.getProjectObject().getId()).isPortal()) {
                customIssueLinkManager.save(issue.getId(), mirrorIssue.getId());
                CustomField portalCaseNumber = getCustomFieldByName(mirrorIssue, PORTAL_CASE_NUMBER);
                if (null != portalCaseNumber) {
                    mirrorIssue.setCustomFieldValue(portalCaseNumber, issue.getKey());
                } else {
                    LOG.warn("Portal Case Number can't be restored");
                }
            } else {
                customIssueLinkManager.save(mirrorIssue.getId(), issue.getId());
                CustomField portalCaseNumber = getCustomFieldByName(issue, PORTAL_CASE_NUMBER);
                if (null != portalCaseNumber) {
                    issue.setCustomFieldValue(portalCaseNumber, mirrorIssue.getKey());
                    if (asAClient) {
                        issue.setReporter(reporter);
                        mirrorIssue.setReporter(reporter);
                    }
                }
            }

            correctVersions(mirrorIssue);
            updatePriority(issue, mirrorIssue);
            issueManager.updateIssue(user, issue, SILENT_ISSUE_UPDATE);
            issueManager.updateIssue(user, mirrorIssue, SILENT_ISSUE_UPDATE);
            cleanWatchers(mirrorIssue);
            reindexIssue(issue);
            reindexIssue(mirrorIssue);
        } catch (CreateException e) {
            LOG.error("Cannot create issue, details ", e);
        } catch (IndexException e) {
            LOG.error("Failed to reindex issue ", e);
        }
    }

    private void processComponents(Issue issue, MutableIssue mirrorIssue, Project relatedProject) {
        List<ProjectComponent> mirrorComponents = new LinkedList<>();
        for (ProjectComponent component : issue.getComponentObjects()) {
            for (ProjectComponent relatedComponent : relatedProject.getProjectComponents()) {
                if (component.getName().equals(relatedComponent.getName())) {
                    mirrorComponents.add(relatedComponent);
                }
            }
        }
        if (mirrorComponents.size() > 0) {
            mirrorIssue.setComponentObjects(mirrorComponents);
        }
    }

    void updateComment(MutableIssue issue, Comment comment) {
        CommentLinkManager.CommentType commentType = extraFieldsService.getProjectExtra(issue.getProjectObject().getId()).isPortal() ? CommentLinkManager.CommentType.INTERNAL : CommentLinkManager.CommentType.PORTAL;
        CommentLink commentLink = commentLinkManager.getCommentByTypeAndId(commentType, comment.getId());
        if (null == commentLink) return;
        CommentManager commentManager = ComponentAccessor.getCommentManager();
        MutableComment commentToChange = commentManager.getMutableComment(CommentLinkManager.CommentType.INTERNAL.equals(commentType) ? commentLink.getPortalCommentId() : commentLink.getInternalCommentId());
        if (null == commentToChange) return;
        doLock(issue, EventType.ISSUE_COMMENT_EDITED_ID);
        if (CommentLinkManager.CommentType.INTERNAL.equals(commentType)) {
            if (!comment.getBody().startsWith(PORTAL_TAG)) {
                commentToChange.setBody(comment.getBody());
                //internal comment always must with PORTAL tag!
                ((MutableComment) comment).setBody(PORTAL_TAG + comment.getBody());
                commentManager.update(comment, false);
            } else {
                commentToChange.setBody(comment.getBody().replaceFirst(PORTAL_TAG, ""));
            }
        } else {  //PORTAL
            commentToChange.setBody(PORTAL_TAG + comment.getBody());
        }
        commentToChange.setUpdated(comment.getUpdated());
        commentToChange.setUpdateAuthor(comment.getUpdateAuthorApplicationUser());
        commentManager.update(commentToChange, true);
    }

    void copyComment(MutableIssue issue, MutableIssue relatedIssue, Comment comment) {
        if (null == comment) return;
        Boolean isPortal = extraFieldsService.getProjectExtra(issue.getProjectObject().getId()).isPortal();
        if (shouldNotifyPortal(comment) || isPortal) {
            if (null != relatedIssue) {
                Project project = issue.getProjectObject();
                Project relatedProject = ComponentAccessor.getProjectManager().getProjectObj(extraFieldsService.getProjectExtra(project.getId()).getRelatedProjectId());
                if (null != relatedProject) {
                    CommentManager commentManager = ComponentAccessor.getCommentManager();
                    Date created = new Date();
                    doLock(relatedIssue, EventType.ISSUE_COMMENTED_ID);
                    String body;
                    if (!isPortal) {
                        body = comment.getBody().replaceFirst(PORTAL_TAG, "");
                    } else {
                        body = PORTAL_TAG + comment.getBody();
                    }
                    Comment createdComment = commentManager.create(relatedIssue, comment.getAuthorApplicationUser(), comment.getUpdateAuthorApplicationUser(), body, null, null, created, created, true, true);
                    if (isPortal) {
                        commentLinkManager.save(issue.getId(), relatedIssue.getId(), comment.getId(), createdComment.getId());
                    } else {
                        commentLinkManager.save(relatedIssue.getId(), issue.getId(), createdComment.getId(), comment.getId());
                    }
                }
            }
        }
    }

    private User getOwner(ProjectRoleManager projectRoleManager, Project relatedProject) {
        User retVal = null;
        ProjectRole projectRole = projectRoleManager.getProjectRole("Portal Owner");
        if (projectRole != null) {
            ProjectRoleActors actors = projectRoleManager.getProjectRoleActors(projectRole, relatedProject);
            Set<User> portalOwnerUsers = actors.getUsers();
            if (portalOwnerUsers.size() > 0 && portalOwnerUsers.iterator().hasNext()) {
                retVal = portalOwnerUsers.iterator().next();
            }
        }
        return retVal;
    }

    private void reindexIssue(Issue issue) throws IndexException {
        boolean oldIndexIssuesValue = ImportUtils.isIndexIssues();
        ImportUtils.setIndexIssues(true);
        IssueIndexManager issueIndexManager = ComponentAccessor.getIssueIndexManager();
        issueIndexManager.reIndex(issue);
        ImportUtils.setIndexIssues(oldIndexIssuesValue);
    }

    MutableIssue getRelatedIssue(Boolean isPortalIssue, Issue currentIssue) {
        CustomIssueLink issueLink = isPortalIssue ? customIssueLinkManager.getLinkBySource(currentIssue.getId()) : customIssueLinkManager.getLinkByTarget(currentIssue.getId());
        if (issueLink != null) {
            return issueManager.getIssueObject(isPortalIssue ? issueLink.getTarget() : issueLink.getSource());
        }
        return null;
    }

    void updateIssue(ApplicationUser user, MutableIssue issue, MutableIssue relatedIssue, Comment comment, Long eventTypeId) {
        relatedIssue.setAffectedVersions(issue.getAffectedVersions());
        relatedIssue.setFixVersions(issue.getFixVersions());
        relatedIssue.setAssignee(issue.getAssignee());
        relatedIssue.setDescription(issue.getDescription());
        relatedIssue.setDueDate(issue.getDueDate());
        relatedIssue.setEnvironment(issue.getEnvironment());
        relatedIssue.setEstimate(issue.getEstimate());
        relatedIssue.setOriginalEstimate(issue.getOriginalEstimate());

        if (extraFieldsService.getProjectExtra(issue.getProjectObject().getId()).isPortal()) {
            updatePriority(issue, relatedIssue);
        } else {
            relatedIssue.setPriorityObject(issue.getPriorityObject());
        }
        relatedIssue.setLabels(issue.getLabels());
        relatedIssue.setIssueTypeObject(issue.getIssueTypeObject());
        relatedIssue.setReporter(issue.getReporter());
        relatedIssue.setSummary(issue.getSummary());
        relatedIssue.setTimeSpent(issue.getTimeSpent());
        relatedIssue.setResolutionObject(issue.getResolutionObject());

        correctVersions(relatedIssue);
        populateCustomFields(issue, relatedIssue);

        syncAttachments(issue, relatedIssue, user);

        issueManager.updateIssue(user, relatedIssue, SILENT_ISSUE_UPDATE);
        copyComment(issue, relatedIssue, comment);

        if (eventTypeId >= 10000) {
            changeIssueStatus(eventTypeId, issue, relatedIssue, user);
        }
        try {
            reindexIssue(issueManager.getIssueObject(relatedIssue.getId()));
        } catch (IndexException e) {
            LOG.error(e);
        }
    }


    private void syncAttachments(MutableIssue issue, MutableIssue relatedIssue, ApplicationUser applicationUser) {
        Project project = issue.getProjectObject();
        Project relatedProject = ComponentAccessor.getProjectManager().getProjectObj(extraFieldsService.getProjectExtra(project.getId()).getRelatedProjectId());
        if (null == relatedProject) return;

        AttachmentManager attachmentManager = ComponentAccessor.getAttachmentManager();
        int currentIssueAttachmentsCount = attachmentManager.getAttachments(issue).size();
        int relatedIssueAttachmentsCount = attachmentManager.getAttachments(relatedIssue).size();
        //attachment was added
        if (currentIssueAttachmentsCount > relatedIssueAttachmentsCount) {
            Set<String> relatedIssueAttachmentKeys = new HashSet<>();
            for (Attachment attachment : attachmentManager.getAttachments(relatedIssue)) {
                relatedIssueAttachmentKeys.add(attachment.getFilename() + "||" + attachment.getFilesize());
            }

            for (Attachment attachment : attachmentManager.getAttachments(issue)) {
                String currentIssueAttachmentKey = attachment.getFilename() + "||" + attachment.getFilesize();
                if (!relatedIssueAttachmentKeys.contains(currentIssueAttachmentKey)) {
                    createAttachment(attachment, relatedIssue, applicationUser);
                }
            }
        }//attachment was removed.
        else if (currentIssueAttachmentsCount < relatedIssueAttachmentsCount) {
            Set<String> currentIssueAttachmentKeys = new HashSet<>();
            for (Attachment attachment : attachmentManager.getAttachments(issue)) {
                currentIssueAttachmentKeys.add(attachment.getFilename() + "||" + attachment.getFilesize());
            }

            for (Attachment attachment : attachmentManager.getAttachments(relatedIssue)) {
                String relatedIssueAttachmentKey = attachment.getFilename() + "||" + attachment.getFilesize();
                if (!currentIssueAttachmentKeys.contains(relatedIssueAttachmentKey)) {
                    removeAttachment(attachment, relatedIssue);
                }
            }
        }
    }

    private void createAttachment(Attachment attachment, MutableIssue relatedIssue, ApplicationUser applicationUser) {
        AttachmentManager attachmentManager = ComponentAccessor.getAttachmentManager();
        File attachmentFile = getAttachmentFile(attachment);
        if (attachmentFile.exists() && attachmentFile.canRead()) {
            try {
                CreateAttachmentParamsBean.Builder builder = new CreateAttachmentParamsBean.Builder();
                builder.file(attachmentFile).filename(attachment.getFilename()).contentType(attachment.getMimetype());
                builder.author(applicationUser).issue(relatedIssue).createdTime(new Timestamp(System.currentTimeMillis()));
                builder.copySourceFile(true);
                attachmentManager.createAttachment(builder.build());
            } catch (AttachmentException e) {
                LOG.warn((new StringBuilder()).append("Could not clone attachment with id '")
                        .append(attachment.getId()).append("' and file path '")
                        .append(attachmentFile.getAbsolutePath()).append("' for issue with id '")
                        .append(relatedIssue.getId()).append("' and key '")
                        .append(relatedIssue.getKey()).append("'.").toString(), e);
            }
        } else {
            LOG.warn((new StringBuilder())
                    .append("Could not clone attachment with id '").append(attachment.getId())
                    .append("' and file path '").append(attachmentFile.getAbsolutePath())
                    .append("' for issue with id '").append(relatedIssue.getId()).append("' and key '")
                    .append(relatedIssue.getKey()).append("', ").append("because the file path ")
                    .append(attachmentFile.exists() ? "is not readable." : "does not exist.").toString());
        }
    }

    private void removeAttachment(Attachment attachment, MutableIssue relatedIssue) {
        File attachmentFile = getAttachmentFile(attachment);
        if (attachmentFile.exists() && attachmentFile.canRead()) {
            try {
                ComponentAccessor.getAttachmentManager().deleteAttachment(attachment);
            } catch (RemoveException e) {
                LOG.warn((new StringBuilder()).append("Could not remove attachment with id '")
                        .append(attachment.getId()).append("' and file path '")
                        .append(attachmentFile.getAbsolutePath()).append("' for issue with id '")
                        .append(relatedIssue.getId()).append("' and key '")
                        .append(relatedIssue.getKey()).append("'.").toString(), e);
            }
        } else {
            LOG.warn((new StringBuilder())
                    .append("Could not remove attachment with id '").append(attachment.getId())
                    .append("' and file path '").append(attachmentFile.getAbsolutePath())
                    .append("' for issue with id '").append(relatedIssue.getId()).append("' and key '")
                    .append(relatedIssue.getKey()).append("', ").append("because the file path ")
                    .append(attachmentFile.exists() ? "is not readable." : "does not exist.").toString());
        }
    }

    /**
     * Clean up link between portal and internal project issue
     *
     * @param issue to clean up
     */
    void removeIssueLink(Issue issue) {
        boolean isPortal = extraFieldsService.getProjectExtra(issue.getProjectObject().getId()).isPortal();
        customIssueLinkManager.remove(issue.getId(), isPortal);
        commentLinkManager.remove(issue.getId());
    }

    /**
     * Remove issue from project
     *
     * @param issue issue which needs to be removed
     */
    void deleteIssue(Issue issue) {
        try {
            issueManager.deleteIssueNoEvent(issue);
        } catch (RemoveException e) {
            LOG.error("Issue '" + issue.getKey() + "' can't be removed, details '" + e.getMessage() + "'");
        }
    }

    private IssueInputParameters getParamsBasedOnAction(MutableIssue issue, Long eventTypeId, ProjectExtraFields extraFields) {
        IssueInputParameters retVal = ComponentAccessor.getIssueService().newIssueInputParameters();

        retVal.setAssigneeId(issue.getAssigneeId());
        if (EventType.ISSUE_RESOLVED_ID.equals(eventTypeId) || EventType.ISSUE_CLOSED_ID.equals(eventTypeId)) {
            if (null != issue.getResolutionObject()) {
                retVal.setResolutionId(issue.getResolutionObject().getId());
            }
            if (issue.getFixVersions().size() != 0) {
                List<Long> fixVersions = new ArrayList<>();
                for (Version version : issue.getFixVersions()) {
                    Version v = versionLinkManager.restoreVersion(version.getId(), !extraFields.isPortal());
                    if (null != v) fixVersions.add(v.getId());
                }
                retVal.setFixVersionIds(fixVersions.toArray(new Long[fixVersions.size()]));
            }
            retVal.setDescription(issue.getDescription());
            retVal.setTimeSpent(issue.getTimeSpent());
            retVal.setRemainingEstimate(issue.getEstimate());
        }
        return retVal;
    }

    void assignee(MutableIssue issue, MutableIssue relatedIssue, Comment comment, ApplicationUser user) {
        if (null != relatedIssue) {
            IssueService issueService = ComponentAccessor.getIssueService();
            IssueService.AssignValidationResult validationResult = issueService.validateAssign(user, relatedIssue.getId(), issue.getAssignee().getName());
            if (validationResult.isValid()) {
                doLock(relatedIssue, EventType.ISSUE_ASSIGNED_ID);
                issueService.assign(user, validationResult);
                MutableIssue mutableIssue = issueManager.getIssueObject(relatedIssue.getId());
                copyComment(issue, mutableIssue, comment);
            } else {
                LOG.error("[" + issue + " => " + relatedIssue + "] Assignee failed!!!\n" + validationResult.getErrorCollection());
            }
        }
    }

    void transitIssue(Long eventTypeId, MutableIssue issue, MutableIssue relatedIssue, ApplicationUser user, Comment comment, ProjectExtraFields extraFields) {
        ActionDescriptor actionDescriptor = actionResolverManager.getActionId(issue, relatedIssue);
        Integer id = null != actionDescriptor ? actionDescriptor.getId() : actionResolverManager.resolve(eventTypeId, relatedIssue);
        IssueInputParameters issueInputParameters = getParamsBasedOnAction(issue, eventTypeId, extraFields);
        IssueService.TransitionValidationResult validationResult = issueTransitionValidator.validateTransition(user, relatedIssue.getId(), id, issueInputParameters, TransitionOptions.defaults());

        if (validationResult.isValid()) {
            doLock(relatedIssue, eventTypeId);
            MutableIssue mutableIssue = issueManager.getIssueObject(relatedIssue.getId());
            copyComment(issue, mutableIssue, comment);
            ComponentAccessor.getIssueService().transition(user, validationResult);
        } else {
            LOG.error(validationResult.getErrorCollection().toString());
        }
    }

    private void changeIssueStatus(Long eventTypeId, MutableIssue issue, MutableIssue relatedIssue, ApplicationUser user) {
        IssueService issueService = ComponentAccessor.getIssueService();
        IssueInputParameters issueInputParameters = ComponentAccessor.getIssueService().newIssueInputParameters();
        ActionDescriptor actionDescriptor = actionResolverManager.getActionId(issue, relatedIssue);
        Integer id = actionDescriptor == null ? actionResolverManager.resolve(eventTypeId, relatedIssue) : actionDescriptor.getId();

        doLock(relatedIssue, eventTypeId);
        IssueService.TransitionValidationResult validationResult = issueTransitionValidator.validateTransition(user, relatedIssue.getId(), id, issueInputParameters, TransitionOptions.defaults());
        if (validationResult.isValid()) {
            issueService.transition(user, validationResult);
        } else {
            LOG.error(validationResult.getErrorCollection().toString());
        }
    }

    private MutableIssue cloneIssue(Issue issue, Project project) {
        Timestamp time = new Timestamp(new Date().getTime());
        IssueFactory issueFactory = ComponentAccessor.getIssueFactory();

        MutableIssue conedIssue = issueFactory.cloneIssue(issue);
        conedIssue.setProjectId(project.getId());
        conedIssue.setIssueTypeId(issue.getIssueTypeObject().getId());
        conedIssue.setCreated(time);
        conedIssue.setUpdated(time);

        if (null != conedIssue.getSecurityLevelId()) {
            conedIssue.setSecurityLevelId(getProjectSecurityLevel(project));
        }

        populateCustomFields(issue, conedIssue);

        return conedIssue;
    }

    private void populateCustomFields(Issue sourceIssue, MutableIssue destinationIssue) {
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        processComponents(sourceIssue, destinationIssue, destinationIssue.getProjectObject());
        List<CustomField> sourceCustomFields = customFieldManager.getCustomFieldObjects(sourceIssue.getProjectObject().getId(), sourceIssue.getIssueTypeObject().getId());

        for (CustomField sourceCustomField : sourceCustomFields) {
            // Do not clone calculated fields
            if (sourceCustomField.getCustomFieldType() instanceof CalculatedCFType) continue;
            //Do not clone watchers fields
            if ((sourceCustomField.getName().equalsIgnoreCase("watchers")) || (sourceCustomField.getNameKey().equalsIgnoreCase("watchers")))
                continue;
            // [CWJIRAUTIL-16] Target/Destination issue does not have a value for this field
            try {
                destinationIssue.getCustomFieldValue(sourceCustomField);
            } catch (Exception e) {
                continue;
            }
            Object currentSourceValue = sourceIssue.getCustomFieldValue(sourceCustomField);
            destinationIssue.setCustomFieldValue(sourceCustomField, currentSourceValue);
        }
    }

    private Long getProjectSecurityLevel(Project project) {
        return ComponentAccessor.getIssueSecurityLevelManager().getDefaultSecurityLevel(project);
    }

    private void cleanWatchers(Issue issue) {
        WatcherManager watcherManager = ComponentAccessor.getWatcherManager();
        List<ApplicationUser> users = ComponentAccessor.getIssueManager().getWatchersFor(issue);
        for (ApplicationUser user : users) {
            watcherManager.stopWatching(user, issue);
        }
    }

    private void updatePriority(MutableIssue issue, MutableIssue mirrorIssue) {
        CustomField impactField = getCustomFieldByName(issue, "Business Impact");
        CustomField urgencyField = getCustomFieldByName(issue, "Urgency");

        String impact = null;
        String urgency = null;
        String type = issue.getIssueTypeObject().getName();

        Integer priority;
        Timestamp dueDate;

        Project portal = issue.getProjectObject();
        PriorityMapper priorityMapper = new PriorityMapper(portal, extraFieldsService);

        if (impactField != null) {
            Object impactValue = issue.getCustomFieldValue(impactField);
            impact = impactValue != null ? impactValue.toString() : null;
        }
        if (urgencyField != null) {
            Object urgencyValue = issue.getCustomFieldValue(urgencyField);
            urgency = urgencyValue != null ? urgencyValue.toString() : null;
        }
        PriorityMapper.MappingStructure mapping = priorityMapper.getMapping(type, urgency, impact);
        if (null != mapping) {
            priority = mapping.getPriority();
            dueDate = new Timestamp(mapping.getDueDate().getTime());
            if (null != priority) {
                mirrorIssue.setDueDate(dueDate);
                mirrorIssue.setPriorityId(Integer.toString(priority));
                issue.setDueDate(dueDate);
                issue.setPriorityId(Integer.toString(priority));
            } else {
                mirrorIssue.setPriorityObject(issue.getPriorityObject());
            }
        } else if (null == issue.getDueDate() && null != issue.getPriorityObject() && null != priorityMapper.getDueDate(issue.getPriorityObject().getName())) {
            dueDate = new Timestamp(priorityMapper.getDueDate(issue.getPriorityObject().getName()).getTime());
            mirrorIssue.setDueDate(dueDate);
            issue.setDueDate(dueDate);
        } else {
            mirrorIssue.setPriorityObject(issue.getPriorityObject());
            LOG.error("Can't find priority mapping for issue: [" + issue + "] mirror: [" + mirrorIssue + "]");
        }
        priorityMapper.clear();
    }

    private void correctVersions(MutableIssue issue) {
        correctAffectedVersions(issue);
        correctFixVersions(issue);
    }

    private void correctFixVersions(MutableIssue issue) {
        Collection<Version> versions = issue.getFixVersions();
        Collection<Version> mirrorVersionsFromProject = new ArrayList<>();

        Project issueProject = issue.getProjectObject();
        boolean isPortal = extraFieldsService.getProjectExtra(issueProject.getId()).isPortal();
        restoreVersions(versions, mirrorVersionsFromProject, isPortal);
        issue.setFixVersions(mirrorVersionsFromProject);
    }

    private void restoreVersions(Collection<Version> versions, Collection<Version> mirrorVersionsFromProject, boolean isPortal) {
        for (Version versionToConvert : versions) {
            Version restoredVersion = versionLinkManager.restoreVersion(versionToConvert.getId(), isPortal);
            if (restoredVersion != null) {
                mirrorVersionsFromProject.add(restoredVersion);
            } else {
                LOG.error("Version with id '" + versionToConvert.getId() + "' can't be restored..");
            }
        }
    }

    private void correctAffectedVersions(MutableIssue issue) {
        Collection<Version> versions = issue.getAffectedVersions();
        Collection<Version> mirrorVersionsFromProject = new ArrayList<>();

        Project issueProject = issue.getProjectObject();
        boolean isPortal = extraFieldsService.getProjectExtra(issueProject.getId()).isPortal();

        restoreVersions(versions, mirrorVersionsFromProject, isPortal);
        issue.setAffectedVersions(mirrorVersionsFromProject);
    }

    boolean shouldNotifyPortal(Comment comment) {
        return null != comment && null != comment.getBody() && comment.getBody().startsWith(PORTAL_TAG);
    }
}
