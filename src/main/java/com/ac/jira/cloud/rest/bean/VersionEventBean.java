package com.ac.jira.cloud.rest.bean;

import com.atlassian.jira.rest.v2.issue.version.VersionBean;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class VersionEventBean {
    @XmlElement(name = "timestamp")
    private Long timestamp;

    @XmlElement(name = "webhookEvent")
    private String webhookEvent;

    @XmlElement(name = "version")
    private VersionBean version;

    @XmlElement(name = "affectsVersionSwappedTo")
    private VersionBean affectsVersionSwappedTo;

    @XmlElement(name = "fixVersionSwappedTo")
    private VersionBean fixVersionSwappedTo;

    public VersionEventBean() {
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
