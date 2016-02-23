package com.ac.jira.cloud.rest.bean;

import com.atlassian.jira.issue.fields.rest.json.beans.CommentJsonBean;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class CommentEventBean {
    @XmlElement(name = "timestamp")
    private Long timestamp;

    @XmlElement(name = "webhookEvent")
    private String webhookEvent;

    @XmlElement(name = "comment")
    private CommentJsonBean comment;

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
