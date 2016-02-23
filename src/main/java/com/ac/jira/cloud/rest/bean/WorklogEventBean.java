package com.ac.jira.cloud.rest.bean;

import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class WorklogEventBean {
    @XmlElement(name = "timestamp")
    private Long timestamp;
    @XmlElement(name = "webhookEvent")
    private String webhookEvent;
    @XmlElement(name = "worklog")
    private Worklog worklog;

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}