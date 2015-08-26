package com.lookout.jenkins;

import hudson.model.Action;
import hudson.tasks.BuildWrapper.Environment;

public class PersistedEnvironment implements Action {
    private Environment environment;

    public PersistedEnvironment(Environment environment) {
        this.environment = environment;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public String getDisplayName() {
        return "Variables From Environment Script";
    }

    // Currently, we don't expose this through the web.
    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}