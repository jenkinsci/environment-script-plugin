package com.lookout.jenkins;

import java.util.Map;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;

public class EnvironmentPluginAction implements EnvironmentContributingAction {

    private transient Map<String, String> envAdditions;
    private transient Map<String, String> envOverrides;

    public EnvironmentPluginAction(Map<String, String> envAdditions, Map<String, String> envOverrides) {
        this.envAdditions = envAdditions;
        this.envOverrides = envOverrides;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "EnvironmentPluginAction";
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {

        if (env == null) {
            return;
        }

        if (envAdditions != null) {
            env.putAll(envAdditions);
        }

        if (envOverrides != null) {
            env.overrideAll(envOverrides);
        }

    }

}
