package com.ac.jira.cloud.rest.bean;

import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement
public class ChangeLog {

    @XmlElement(name = "id")
    private String id;
    @XmlElement(name = "items")
    private List<ChangeItem> changeItems;

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
