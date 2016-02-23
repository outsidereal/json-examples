package com.ac.jira.cloud.rest.bean;

import com.atlassian.jira.issue.fields.rest.json.beans.UserJsonBean;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.xml.bind.annotation.XmlElement;

public class Worklog {
    @XmlElement(name = "self")
    private String self;
    @XmlElement(name = "author")
    private UserJsonBean author;
    @XmlElement(name = "updateAuthor")
    private UserJsonBean updateAuthor;
    @XmlElement(name = "comment")
    private String comment;
    @XmlElement(name = "created")
    private String created;
    @XmlElement(name = "updated")
    private String updated;
    @XmlElement(name = "started")
    private String started;
    @XmlElement(name = "timeSpent")
    private String timeSpent;
    @XmlElement(name = "timeSpentSeconds")
    private Long timeSpentSeconds;
    @XmlElement(name = "id")
    private String id;
    @XmlElement(name = "issueId")
    private String issueId;

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
