package com.ac.jira.cloud.rest.bean;

import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ChangeItem {
    @XmlElement(name = "field")
    private String field;
    @XmlElement(name = "fieldtype")
    private String fieldtype;
    @XmlElement(name = "from")
    private String from;
    @XmlElement(name = "fromString")
    private String fromString;
    @XmlElement(name = "to")
    private String to;
    @XmlElement(name = "toString")
    private String toString;

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
