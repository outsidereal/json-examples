package com.ac.jira.cloud;

import com.ai.jira.portal.ao.issue.CustomIssueLinkManager;
import com.ai.jira.portal.ao.version.VersionLinkManager;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.UpdateIssueRequest;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import org.apache.log4j.Logger;

import java.sql.Timestamp;
import java.util.*;

import static com.atlassian.jira.component.ComponentAccessor.*;
import static com.atlassian.jira.event.type.EventDispatchOption.DO_NOT_DISPATCH;
import static com.atlassian.jira.util.ImportUtils.isIndexIssues;
import static com.atlassian.jira.util.ImportUtils.setIndexIssues;

public class CloudIssueSynchronizer {
    private static final Logger LOG = Logger.getLogger(CloudIssueSynchronizer.class);
    private static final long PORTAL_KEY_CF_ID = 10937L;
    private static final long BUSINESS_IMPACT_CF_ID = 10913L;
    private static final long URGENCY_CF_ID = 10953L;
    private static final ApplicationUser SUPPORT = ComponentAccessor.getUserManager().getUserByName("support");
    private static final UpdateIssueRequest SILENT_ISSUE_UPDATE = UpdateIssueRequest.builder().sendMail(false).eventDispatchOption(DO_NOT_DISPATCH).build();

    private final CustomIssueLinkManager customIssueLinkManager;
    private final VersionLinkManager versionLinkManager;


    public CloudIssueSynchronizer(CustomIssueLinkManager customIssueLinkManager, VersionLinkManager versionLinkManager) {
        this.customIssueLinkManager = customIssueLinkManager;
        this.versionLinkManager = versionLinkManager;
    }


    public void createInternalIssue(ApplicationUser client, MutableIssue remoteIssue, Project internalProject) {
        IssueManager issueManager = ComponentAccessor.getIssueManager();

        MutableIssue internalIssue = cloneIssue(remoteIssue, internalProject);
        processComponents(remoteIssue, internalIssue, internalProject);
        internalIssue.setAssignee(SUPPORT.getDirectoryUser());
        internalIssue.setReporter(client.getDirectoryUser());

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("issue", internalIssue);
            internalIssue = (MutableIssue) issueManager.createIssueObject(client.getDirectoryUser(), context);
            //createAttachments(remoteIssue, internalIssue, client); - should be investigated
            reindexIssue(internalIssue);

            customIssueLinkManager.save(remoteIssue.getId(), internalIssue.getId());

            CustomField portalKey = getCustomFieldManager().getCustomFieldObject(PORTAL_KEY_CF_ID);
            internalIssue.setCustomFieldValue(portalKey, remoteIssue.getKey());

            correctVersions(internalIssue);
            //updatePriority(remoteIssue, internalIssue); - to investigate
            issueManager.updateIssue(client, internalIssue, SILENT_ISSUE_UPDATE);

            reindexIssue(internalIssue);
        } catch (CreateException e) {
            LOG.error("Cannot create issue, details ", e);
        } catch (IndexException e) {
            LOG.error("Failed to reindex issue ", e);
        }
    }

    private MutableIssue cloneIssue(Issue remoteIssue, Project internalProject) {
        Timestamp currentTime = new Timestamp(new Date().getTime());
        MutableIssue internalIssue = getIssueFactory().cloneIssue(remoteIssue);
        internalIssue.setProjectObject(internalProject);
        internalIssue.setIssueTypeObject(remoteIssue.getIssueTypeObject());
        internalIssue.setCreated(currentTime);
        internalIssue.setUpdated(currentTime);
        //copyCustomFieldsValues(remoteIssue, internalIssue); - it's tricky for now

        return internalIssue;
    }

    private void processComponents(Issue remoteIssue, MutableIssue internalIssue, Project internalProject) {
        List<ProjectComponent> internalComponents = new LinkedList<>();
        for (ProjectComponent component : remoteIssue.getComponentObjects()) {
            for (ProjectComponent internalComponent : internalProject.getProjectComponents()) {
                if (component.getName().equals(internalComponent.getName())) {
                    internalComponents.add(internalComponent);
                }
            }
        }
        if (internalComponents.size() > 0) {
            internalIssue.setComponentObjects(internalComponents);
        }
    }


    private void reindexIssue(Issue issue) throws IndexException {
        boolean oldIndexIssuesValue = isIndexIssues();
        setIndexIssues(true);
        getIssueIndexManager().reIndex(issue);
        setIndexIssues(oldIndexIssuesValue);
    }

    private void correctVersions(MutableIssue internalIssue) {
        correctAffectedVersions(internalIssue);
        correctFixVersions(internalIssue);
    }

    private void correctAffectedVersions(MutableIssue internalIssue) {
        Collection<Version> versions = internalIssue.getAffectedVersions();
        Collection<Version> internalVersions = restoreVersions(versions);
        internalIssue.setAffectedVersions(internalVersions);
    }

    private void correctFixVersions(MutableIssue internalIssue) {
        Collection<Version> versions = internalIssue.getFixVersions();
        Collection<Version> internalVersions = restoreVersions(versions);
        internalIssue.setFixVersions(internalVersions);
    }

    private Collection<Version> restoreVersions(Collection<Version> versions) {
        Collection<Version> internalVersions = new ArrayList<>();
        for (Version versionToConvert : versions) {
            Version restoredVersion = versionLinkManager.restoreVersion(versionToConvert.getId(), false);
            if (restoredVersion != null) {
                internalVersions.add(restoredVersion);
            } else {
                LOG.error("Version with id '" + versionToConvert.getId() + "' can't be restored..");
            }
        }
        return internalVersions;
    }

}
