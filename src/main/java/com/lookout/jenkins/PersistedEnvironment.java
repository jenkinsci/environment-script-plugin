package com.lookout.jenkins;

import hudson.EnvVars;
import hudson.model.Action;
import hudson.tasks.BuildWrapper.Environment;

public class PersistedEnvironment implements Action {
	private Environment environment;

	public PersistedEnvironment (Environment environment) {
		this.environment = environment;
	}

	public Environment getEnvironment () {
		return environment;
	}

	public String getDisplayName() {
		return "Environment Script variables";
	}

	public String getIconFileName() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getUrlName() {
		// TODO Auto-generated method stub
		return null;
	}

}