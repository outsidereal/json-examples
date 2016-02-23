package com.ai.jira;

import com.atlassian.event.api.EventListener;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.plugin.event.events.PluginModuleDisabledEvent;
import com.atlassian.plugin.event.events.PluginModuleEnabledEvent;

public class PluginLifecycleListener {

    @EventListener
    public void moduleEnabledEventListener(PluginModuleEnabledEvent moduleEnabledEvent) {
        ModuleDescriptor descriptor = moduleEnabledEvent.getModule();
        if ("com.ai.jira.portal.portal:reports-panel".equals(descriptor.getCompleteKey())) {
            ComponentAccessor.getPluginController().disablePluginModule("com.atlassian.jira.jira-projects-plugin:reports-panel");
        } else if ("com.ai.jira.portal.portal:report-page".equals(descriptor.getCompleteKey())) {
            ComponentAccessor.getPluginController().disablePluginModule("com.atlassian.jira.jira-projects-plugin:report-page");
        }
    }

    @EventListener
    public void moduleDisabledEventListener(PluginModuleDisabledEvent moduleDisabledEvent) {
        ModuleDescriptor descriptor = moduleDisabledEvent.getModule();
        if ("com.ai.jira.portal.portal:reports-panel".equals(descriptor.getCompleteKey())) {
            ComponentAccessor.getPluginController().enablePluginModule("com.atlassian.jira.jira-projects-plugin:reports-panel");
        } else if ("com.ai.jira.portal.portal:report-page".equals(descriptor.getCompleteKey())) {
            ComponentAccessor.getPluginController().enablePluginModule("com.atlassian.jira.jira-projects-plugin:report-page");
        }
    }
}
