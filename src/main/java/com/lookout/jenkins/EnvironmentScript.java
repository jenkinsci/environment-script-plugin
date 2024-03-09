package com.lookout.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.lookout.jenkins.commands.Commands;
import com.lookout.jenkins.commands.PowerShell;
import com.lookout.jenkins.commands.Shebangs;
import com.lookout.jenkins.commands.UnixShell;
import com.lookout.jenkins.commands.WinBatch;

/**
 * Runs a specific chunk of code before each build, parsing output for new
 * environment variables.
 *
 * @author Jørgen P. Tjernø
 * @author dawidmalina@gmail.com
 */
public class EnvironmentScript extends BuildWrapper implements MatrixAggregatable {
    private final String script;
    private final String scriptType;
    private final boolean runOnlyOnParent;
    private final boolean hideEnvironmentVariablesValues;

    @DataBoundConstructor
    public EnvironmentScript(String script, String scriptType, boolean runOnlyOnParent,
            boolean hideEnvironmentVariablesValues) {
        this.script = script;
        this.scriptType = scriptType;
        this.runOnlyOnParent = runOnlyOnParent;
        this.hideEnvironmentVariablesValues = hideEnvironmentVariablesValues;
    }

    /**
     * We'll use this from the "config.jelly".
     */
    public String getScript() {
        return script;
    }

    /**
     * We will use this from the "config.jelly".
     * 
     * @return
     */
    public String getScriptType() {
        return scriptType;
    }

    /**
     * @return Whether or not we only run this on the {@link MatrixBuild} parent, or
     *         on the individual {@link MatrixRun}
     *         s.
     */
    public boolean isRunOnlyOnParent() {
        return runOnlyOnParent;
    }

    public boolean isHideEnvironmentVariablesValues() {
        return hideEnvironmentVariablesValues;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Environment setUp(AbstractBuild build,
            final Launcher launcher,
            final BuildListener listener) throws IOException, InterruptedException {
        if ((build instanceof MatrixRun) && isRunOnlyOnParent()) {
            // If this is a matrix run and we have the onlyRunOnParent option
            // enabled, we just retrieve the persisted environment from the
            // PersistedEnvironment Action.
            MatrixBuild parent = ((MatrixRun) build).getParentBuild();
            if (parent != null) {
                PersistedEnvironment persisted = parent.getAction(PersistedEnvironment.class);
                if (persisted != null) {
                    return persisted.getEnvironment();
                } else {
                    listener.error(
                            "[environment-script] Unable to load persisted environment from matrix parent job, not injecting any variables");
                    return new Environment() {
                    };
                }
            } else {
                // If there's no parent, then the module build was triggered
                // manually, so we generate a new environment.
                return generateEnvironment(build, launcher, listener);
            }
        } else {
            // Otherwise we generate a new one.
            return generateEnvironment(build, launcher, listener);
        }
    }

    private Environment generateEnvironment(AbstractBuild<?, ?> build,
            final Launcher launcher,
            final BuildListener listener) throws IOException, InterruptedException {
        // First we create the script in a temporary directory.
        FilePath ws = build.getWorkspace(), scriptFile = null;

        if (ws == null) {
            listener.error(Messages.EnvironmentScriptWrapper_WorkspaceIsNull());
            return null;
        }

        ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
        int returnCode = -1;
        try {
            // Calculate extension
            String extension = ".sh";
            if (Commands.POWER_SHELL.equals(scriptType)) {
                extension = ".ps1";
            } else if (Commands.BATCH_SCRIPT.equals(scriptType)) {
                extension = ".bat";
            }

            // Make sure prefix will always be more than 3 letters
            // Replace the equals sign with an underscore because Windows doesn't accept
            // this
            final String prefix = "env-" + build.getProject().getName().replace("=", "_");
            // Create a file in the system temporary directory with our script in it.
            scriptFile = ws.createTextTempFile(prefix, extension, script, false);

            // Then we execute the script, putting STDOUT in commandOutput.
            returnCode = launcher.launch().cmds(buildCommandLine(scriptFile))
                    .envs(build.getEnvironment(listener))
                    .stderr(listener.getLogger())
                    .stdout(commandOutput)
                    .pwd(ws).join();
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError(Messages.EnvironmentScriptWrapper_UnableToProduceScript()));
            return null;
        } finally {
            // Make sure we clean scriptFile
            if (scriptFile != null && scriptFile.exists()) {
                scriptFile.delete();
            }
        }

        if (returnCode != 0) {
            listener.fatalError(Messages.EnvironmentScriptWrapper_UnableToExecuteScript(returnCode));
            return null;
        }

        // Pass the output of the command to the Properties loader.
        ByteArrayInputStream propertiesInput = new ByteArrayInputStream(commandOutput.toByteArray());
        InputStreamReader propertiesInputReader = new InputStreamReader(propertiesInput, "UTF-8");
        Properties properties = new Properties();
        try {
            properties.load(propertiesInputReader);
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError(Messages.EnvironmentScriptWrapper_UnableToParseScriptOutput()));
            return null;
        }

        // We sort overrides and additions into two different buckets, because they have
        // to be processed in sequence.
        // See hudson.EnvVars.override for how this logic works.
        final Map<String, String> envAdditions = new HashMap<String, String>(),
                envOverrides = new HashMap<String, String>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            StringBuilder output = new StringBuilder();
            output.append("[environment-script] Adding variable '").append(key).append("'");

            // If hideGeneratedValue is set to true we will hide generated value from log
            // message
            if (!isHideEnvironmentVariablesValues()) {
                output.append(" with value '").append(value).append("'");
            }

            listener.getLogger().println(output.toString());

            if (key.indexOf('+') > 0)
                envOverrides.put(key, value);
            else
                envAdditions.put(key, value);
        }

        build.addAction(new EnvironmentPluginAction(envAdditions, envOverrides));

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
        if (Commands.POWER_SHELL.equals(scriptType)) {
            return PowerShell.buildCommandLine(scriptFile);
        } else if (Commands.BATCH_SCRIPT.equals(scriptType)) {
            return WinBatch.buildCommandLine(scriptFile);
        } else {
            if (Commands.isShebangs(script)) {
                return Shebangs.parseCommandLine(script, scriptFile);
            }
            return UnixShell.buildCommandLine(scriptFile);
        }
    }

    /**
     * Create an aggregator that will calculate the environment once if
     * onlyRunOnParent is true.
     *
     * The aggregator we return is called on the parent job for matrix jobs. In it
     * we generate the environment once and
     * persist it in an Action (of type {@link PersistedEnvironment}) if the job has
     * onlyRunOnParent enabled. The
     * subjobs ("configuration runs") will retrieve this and apply it to their
     * environment, without performing the
     * calculation.
     */
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        if (!isRunOnlyOnParent()) {
            return null;
        }

        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean startBuild() throws InterruptedException, IOException {
                Environment env = generateEnvironment(build, launcher, listener);
                if (env == null) {
                    return false;
                }

                build.addAction(new PersistedEnvironment(env));
                build.getEnvironments().add(env);
                return true;
            }
        };
    }

    @Override
    public Descriptor<BuildWrapper> getDescriptor() {
        return super.getDescriptor();
    }

    /**
     * Descriptor for {@link EnvironmentScript}. Used as a singleton. The class is
     * marked as public so that it can be
     * accessed from views.
     *
     */
    @Extension
    public static final class EnvironmentScriptDescriptor extends BuildWrapperDescriptor {

        public EnvironmentScriptDescriptor() {
            super(EnvironmentScript.class);
            // Load the persisted properties from file.
            load();
        }

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

        public boolean isMatrix(StaplerRequest request) {
            return Boolean.FALSE;
        }

        public ListBoxModel doFillScriptTypeItems() {
            ListBoxModel items = new ListBoxModel(
                    new ListBoxModel.Option(Commands.UNIX_SCRIPT_DISPLAY_NAME, Commands.UNIX_SCRIPT),
                    new ListBoxModel.Option(Commands.BATCH_SCRIPT_DISPLAY_NAME, Commands.BATCH_SCRIPT),
                    new ListBoxModel.Option(Commands.POWER_SHELL_DISPLAY_NAME, Commands.POWER_SHELL));
            return items;
        }
    }
}
