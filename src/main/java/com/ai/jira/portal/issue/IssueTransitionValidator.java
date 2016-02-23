package com.ai.jira.portal.issue;

import com.atlassian.jira.bc.issue.DefaultIssueService;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.changehistory.metadata.HistoryMetadata;
import com.atlassian.jira.issue.fields.screen.FieldScreenRenderer;
import com.atlassian.jira.issue.fields.screen.FieldScreenRendererFactory;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.TransitionOptions;
import com.atlassian.jira.workflow.WorkflowManager;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Created by d.ulanovych on 07.08.2015.
 */
public class IssueTransitionValidator {
    private static final Logger LOG = Logger.getLogger(IssueTransitionValidator.class);
    private final IssueManager issueManager = ComponentAccessor.getIssueManager();
    private final FieldScreenRendererFactory fieldScreenRendererFactory = ComponentAccessor.getFieldScreenRendererFactory();
    private final WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    private final IssueService issueService = ComponentAccessor.getIssueService();

    public IssueService.TransitionValidationResult validateTransition(ApplicationUser user, Long issueId, int actionId, IssueInputParameters issueInputParameters, TransitionOptions transitionOptions) {
        if (issueInputParameters == null) {
            throw new IllegalArgumentException("You must provide a non-null issueInputParameters.");
        }
        final I18nHelper i18n = new I18nBean(user);
        final Map<String, Object> fieldValuesHolder = cloneFieldValuesHolder(issueInputParameters);
        final SimpleErrorCollection errors = new SimpleErrorCollection();
        if (issueId == null) {
            errors.addErrorMessage(i18n.getText("issue.service.transition.issue.is.null"));
            return new IssueService.TransitionValidationResult(null, errors, fieldValuesHolder, null, actionId);
        }

        // Try to lookup the issue that we must update
        final MutableIssue issue = issueManager.getIssueObject(issueId);

        if (issue == null) {
            errors.addErrorMessage(i18n.getText("issue.service.transition.issue.is.null"));
            return new IssueService.TransitionValidationResult(null, errors, fieldValuesHolder, null, actionId);
        }
        String originalAssigneeId = issue.getAssigneeId();

        final ActionDescriptor actionDescriptor = getActionDescriptor(issue, actionId);

        // Validate that the action exists
        if (actionDescriptor == null) {
            errors.addErrorMessage(i18n.getText("issue.service.transition.issue.no.action", String.valueOf(actionId)));
            return new IssueService.TransitionValidationResult(null, errors, fieldValuesHolder, null, actionId);
        }

        MutableIssue updatedIssue;
        // We only want to update the issue if there is an associated screen with the transition
        if (StringUtils.isNotEmpty(actionDescriptor.getView())) {
            updatedIssue = validateAndUpdateIssueFromFields(user, issue, issueInputParameters, fieldValuesHolder,
                    errors, i18n, getTransitionFieldScreenRenderer(issue, actionDescriptor), false, actionId);

            if (errors.hasAnyErrors()) {
                return new IssueService.TransitionValidationResult(null, errors, fieldValuesHolder, null, actionId);
            }
            // Comment information is handled by the generated change history so no need to put it into the additional
            // parameters
        } else {
            updatedIssue = issue;
        }

        final Map<String, Object> additionalParams = createAdditionalParameters(user, fieldValuesHolder, transitionOptions, issueInputParameters.getHistoryMetadata(), originalAssigneeId);
        return new IssueService.TransitionValidationResult(updatedIssue, errors, fieldValuesHolder, additionalParams, actionId);
    }

    private Map<String, Object> cloneFieldValuesHolder(IssueInputParameters issueInputParameters) {
        Method cloneFieldValuesHolder;
        try {
            cloneFieldValuesHolder = DefaultIssueService.class.getDeclaredMethod("cloneFieldValuesHolder", IssueInputParameters.class);
        } catch (NoSuchMethodException e) {
            LOG.error(e);
            return null;
        }
        cloneFieldValuesHolder.setAccessible(true);
        Map<String, Object> result = null;
        try {
            result = (Map<String, Object>) cloneFieldValuesHolder.invoke(issueService, issueInputParameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error(e);
        }
        return result;
    }

    ActionDescriptor getActionDescriptor(Issue issue, int actionId) {
        final JiraWorkflow workflow = workflowManager.getWorkflow(issue);
        if (workflow == null) {
            return null;
        }

        final WorkflowDescriptor descriptor = workflow.getDescriptor();
        if (descriptor == null) {
            return null;
        }
        return descriptor.getAction(actionId);
    }

    FieldScreenRenderer getTransitionFieldScreenRenderer(Issue issue, ActionDescriptor actionDescriptor) {
        return fieldScreenRendererFactory.getFieldScreenRenderer(issue, actionDescriptor);
    }

    @Nullable
    MutableIssue validateAndUpdateIssueFromFields(ApplicationUser user, MutableIssue issue, IssueInputParameters issueInputParameters,
                                                  Map<String, Object> fieldValuesHolder, ErrorCollection errorCollection, I18nHelper i18n,
                                                  final FieldScreenRenderer fieldScreenRenderer, boolean updateComment, @Nullable Integer workflowActionId) {
        Method validateAndUpdateIssueFromFields;
        try {
            validateAndUpdateIssueFromFields = DefaultIssueService.class.getDeclaredMethod("validateAndUpdateIssueFromFields",
                    ApplicationUser.class,
                    MutableIssue.class,
                    IssueInputParameters.class,
                    Map.class,
                    ErrorCollection.class,
                    I18nHelper.class,
                    FieldScreenRenderer.class,
                    boolean.class,
                    Integer.class
            );
        } catch (NoSuchMethodException e) {
            LOG.error(e);
            return null;
        }
        validateAndUpdateIssueFromFields.setAccessible(true);
        MutableIssue mutableIssue = null;
        try {
            mutableIssue = (MutableIssue) validateAndUpdateIssueFromFields.invoke(issueService, user, issue, issueInputParameters, fieldValuesHolder, errorCollection, i18n, fieldScreenRenderer, updateComment, workflowActionId);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error(e);
        }
        return mutableIssue;
    }

    Map<String, Object> createAdditionalParameters(final ApplicationUser user, final Map<String, Object> fieldValuesHolder, TransitionOptions transitionOptions,
                                                   final HistoryMetadata historyMetadata, final String originalAssigneeId) {

        Method createAdditionalParameters = null;

        try {
            createAdditionalParameters = DefaultIssueService.class.getDeclaredMethod("createAdditionalParameters",
                    ApplicationUser.class,
                    Map.class,
                    TransitionOptions.class,
                    HistoryMetadata.class,
                    String.class);
        } catch (NoSuchMethodException e) {
            LOG.error(e);
        }
        createAdditionalParameters.setAccessible(true);
        Map<String, Object> additionalParams = null;
        try {
            additionalParams = (Map<String, Object>) createAdditionalParameters.invoke(issueService, user, fieldValuesHolder, transitionOptions, historyMetadata, originalAssigneeId);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error(e);
        }

        return additionalParams;
    }
}