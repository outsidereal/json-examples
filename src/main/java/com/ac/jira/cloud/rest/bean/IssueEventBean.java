package com.ac.jira.cloud.rest.bean;

import com.atlassian.jira.issue.fields.rest.json.beans.CommentsWithPaginationJsonBean;
import com.atlassian.jira.issue.fields.rest.json.beans.UserJsonBean;
import com.atlassian.jira.rest.v2.issue.IssueBean;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class IssueEventBean {

    @XmlElement(name = "timestamp")
    private Long timestamp;

    @XmlElement(name = "webhookEvent")
    private String webhookEvent;

    @XmlElement(name = "user")
    private UserJsonBean user;

    @XmlElement(name = "issue")
    private IssueBean issue;

    @XmlElement(name = "comment")
    private CommentsWithPaginationJsonBean comment;

    @XmlElement(name = "changelog")
    private ChangeLog changeLog;

    public IssueBean getIssue() {
        return issue;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
