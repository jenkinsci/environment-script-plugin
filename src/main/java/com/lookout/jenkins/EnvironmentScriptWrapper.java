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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 *
 * @author Jørgen P. Tjernø
 */
public class EnvironmentScriptWrapper extends BuildWrapper {
	private final String script;

	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
	@DataBoundConstructor
	public EnvironmentScriptWrapper(String script) {
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
			BuildListener listener) throws IOException, InterruptedException {

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
		final List<Map.Entry<String, String>> injectedVariables = new ArrayList<Map.Entry<String, String>>(lines.length);
		for (String line : lines)
		{
			if (line.trim().isEmpty()) {
				continue;
			}

			String[] keyAndValue = line.split("=", 2);
			if (keyAndValue.length < 2) {
				listener.error("[environment-script] Invalid line encountered, ignoring: " + line);
			} else {
				Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(keyAndValue[0], keyAndValue[1]);
				listener.getLogger().println("[environment-script] Adding variable '" + entry.getKey() + "' with value '" + entry.getValue() + "'");
				injectedVariables.add(entry);
			}
		}

		return new Environment() {
			@Override
			public void buildEnvVars(Map<String, String> env) {
				// Here we evaluate the variables we parsed above, in order.
				// We expand against all the environment variables we have so far.
				EnvVars vars = new EnvVars(env);
				for (Map.Entry<String, String> variable : injectedVariables) {
					env.put(variable.getKey(), vars.expand(variable.getValue()));
				}
			}
		};
	}

	// Mostly stolen from hudson.tasks.Shell.buildCommandLine.
	public String[] buildCommandLine(FilePath scriptFile) {
		// Respect shebangs
		if(script.startsWith("#!")) {
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
	 * Descriptor for {@link EnvironmentScriptWrapper}. Used as a singleton.
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

