package com.lookout.jenkins;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Shell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Runs a specific chunk of code before each build, parsing output for new environment variables.
 *
 * @author Jørgen P. Tjernø
 */
public class EnvironmentScript extends BuildWrapper {
	private final String script;

	@DataBoundConstructor
	public EnvironmentScript(String script) {
		this.script = script;
	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	public String getScript() {
		return script;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Environment setUp(AbstractBuild build,
			final Launcher launcher,
			final BuildListener listener) throws IOException, InterruptedException {

		// First we create the script in a temporary directory.
		FilePath ws = build.getWorkspace();
		FilePath scriptFile;
		try {
			// Create a file in the system temporary directory with our script in it.
			scriptFile = ws.createTextTempFile(build.getProject().getName(), ".sh", script, false);
		} catch (IOException e) {
			Util.displayIOException(e,listener);
			e.printStackTrace(listener.fatalError(Messages.EnvironmentScriptWrapper_UnableToProduceScript()));
			return null;
		}

		// Then we execute the script, putting STDOUT in commandOutput.
		ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
		int returnCode =
			launcher.launch().cmds(buildCommandLine(scriptFile))
			.envs(build.getEnvironment(listener))
			.stderr(listener.getLogger())
			.stdout(commandOutput)
			.pwd(ws).join();

		if (returnCode != 0)
		{
			listener.fatalError(Messages.EnvironmentScriptWrapper_UnableToExecuteScript(returnCode));
			return null;
		}


		// Then we parse the variables out of it. We could use java.util.Properties, but it doesn't order the properties, so expanding variables with previous variables (like a shell script expects) doesn't work.
		String[] lines = commandOutput.toString().split("(\n|\r\n)");
		final Map<String, String> envAdditions = new HashMap<String, String>(lines.length);
		final Map<String, String> envOverrides = new HashMap<String, String>();
		for (String line : lines)
		{
			if (line.trim().isEmpty()) {
				continue;
			}

			String[] keyAndValue = line.split("=", 2);
			if (keyAndValue.length < 2) {
				listener.error("[environment-script] Invalid line encountered, ignoring: " + line);
			} else {
				listener.getLogger().println("[environment-script] Adding variable '" + keyAndValue[0] + "' with value '" + keyAndValue[1] + "'");

				// We sort overrides and additions into two different buckets, because they have to be processed in sequence.
				// See hudson.EnvVars.override for how this logic works.
				if (keyAndValue[0].indexOf('+') > 0)
					envOverrides.put(keyAndValue[0], keyAndValue[1]);
				else
					envAdditions.put(keyAndValue[0], keyAndValue[1]);
			}
		}

		return new Environment() {
			@Override
			public void buildEnvVars(Map<String, String> env) {
				// A little roundabout, but allows us to do overrides per
				// how EnvVars#override works (PATH+unique=/foo/bar)
				EnvVars envVars = new EnvVars(env);
				envVars.putAll(envAdditions);
				envVars.overrideAll(envOverrides);
				env.putAll(envVars);
			}
		};
	}

	// Mostly stolen from hudson.tasks.Shell.buildCommandLine.
	public String[] buildCommandLine(FilePath scriptFile) {
		// Respect shebangs
		if (script.startsWith("#!")) {
			// Find first line, or just entire script if it's one line.
			int end = script.indexOf('\n');
			if (end < 0)
				end = script.length();

			String shell = script.substring(0, end).trim();
			shell = shell.substring(2);

			List<String> args = Arrays.asList(Util.tokenize(shell));
			args.add(scriptFile.getRemote());

			return args.toArray(new String[args.size()]);
		} else  {
			Shell.DescriptorImpl shellDescriptor = Jenkins.getInstance().getDescriptorByType(Shell.DescriptorImpl.class);
			String shell = shellDescriptor.getShellOrDefault(scriptFile.getChannel());
			return new String[] { shell, "-xe", scriptFile.getRemote() };
		}
	}

	/**
	 * Descriptor for {@link EnvironmentScript}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 */
	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Generate environment variables from script";
		}

		@Override
		public boolean isApplicable(AbstractProject<?, ?> project) {
			return true;
		}
	}
}

