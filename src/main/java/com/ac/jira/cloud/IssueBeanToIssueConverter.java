package com.ac.jira.cloud;

import com.atlassian.jira.issue.IssueImpl;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.label.LabelManager;
import com.atlassian.jira.rest.v2.issue.IssueBean;
import org.ofbiz.core.entity.GenericValue;

import static com.atlassian.jira.component.ComponentAccessor.*;

public class IssueBeanToIssueConverter {

    public static MutableIssue toIssue(IssueBean issueBean) {
        MutableIssue issue = new IssueImpl((GenericValue) null, getIssueManager(), getProjectManager(), getVersionManager(),
                getIssueSecurityLevelManager(), getConstantsManager(), getSubTaskManager(), getAttachmentManager(),
                getComponent(LabelManager.class), getProjectComponentManager(), getUserManager(), getJiraAuthenticationContext());


        return issue;
    }
}
