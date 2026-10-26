package com.lookout.jenkins;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

import hudson.EnvVars;

import static org.junit.jupiter.api.Assertions.*;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.TaskListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.StreamTaskListener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class EnvironmentScriptTest {

    private JenkinsRule jenkins;

    class TestJob {
        private final FreeStyleProject project;
        private final FreeStyleBuild build;
        private final TaskListener listener;

        TestJob(String script, String scriptType, boolean hideEnvironmentVariablesValues) throws Exception {
            listener = new StreamTaskListener(System.err, Charset.defaultCharset());
            project = jenkins.createFreeStyleProject();
            project.getBuildWrappersList()
                    .add(new EnvironmentScript(script, scriptType, false, hideEnvironmentVariablesValues));
            project.setScm(new SingleFileSCM("envs", "foo_var=bar"));
            build = jenkins.buildAndAssertSuccess(project);
            jenkins.waitUntilNoActivity();
        }
    }

    private final static String UNIX_SCRIPT = "unixScript";
    private final static String BATCH_SCRIPT = "batchScript";

    private final static String SCRIPT_SIMPLE_VARIABLES = """
        echo var1=one
        echo var2=two
        echo var3=three""";

    private final static String SCRIPT_DEPENDENT_VARIABLES_UNIX = """
        echo var1=one
        echo var2='$var1 two'
        echo var3='yo $var4'
        echo var4='three ${var2}'""";

    private final static String SCRIPT_DEPENDENT_VARIABLES_BATCH = """
        echo var1=one
        echo var2=$var1 two
        echo var3=yo $var4
        echo var4=three ${var2}""";

    private final static String SCRIPT_OVERRIDDEN_VARIABLES_UNIX = """
        echo var1=one
        echo var1+something='not one'
        echo var2+something='two'""";

    private final static String SCRIPT_OVERRIDDEN_VARIABLES_BATCH = """
        echo var1=one
        echo var1+something=not one
        echo var2+something=two""";

    private final static String SCRIPT_UTF8 = "echo UTFstr=mąż";

    private final static String SCRIPT_SHEBANG_UNIX = "#!/bin/cat\n"
            + "hello=world";

    // batch script does not have shebang
    private final static String SCRIPT_SHEBANG_BATCH = "echo hello=world";

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkins = rule;
    }

    @Test
    void testConfigurationRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        String scriptType = Functions.isWindows() ? BATCH_SCRIPT : UNIX_SCRIPT;
        for (boolean hideValues : new boolean[] {true, false}) {
            EnvironmentScript wrapper = new EnvironmentScript(SCRIPT_SIMPLE_VARIABLES, scriptType, false, hideValues);
            project.getBuildWrappersList().clear();
            project.getBuildWrappersList().add(wrapper);

            jenkins.configRoundtrip(project);

            EnvironmentScript configured = project.getBuildWrappersList().get(EnvironmentScript.class);
            assertNotNull(configured);
            jenkins.assertEqualDataBoundBeans(wrapper, configured);
        }
    }

    @Test
    void testWithEmptyScript() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob("", scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());
    }

    @Test
    void testWithSimpleVariables() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob(SCRIPT_SIMPLE_VARIABLES, scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());

        EnvVars vars = job.build.getEnvironment(job.listener);
        assertEquals("one", vars.get("var1"));
        assertEquals("two", vars.get("var2"));
        assertEquals("three", vars.get("var3"));
    }

    @Test
    void testWithDependentVariables() throws Exception {
        String scriptType = UNIX_SCRIPT;
        String script = SCRIPT_DEPENDENT_VARIABLES_UNIX;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
            script = SCRIPT_DEPENDENT_VARIABLES_BATCH;
        }
        TestJob job = new TestJob(script, scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());

        EnvVars vars = job.build.getEnvironment(job.listener);
        assertEquals("one", vars.get("var1"));
        assertEquals("one two", vars.get("var2"));
        assertEquals("yo three ${var2}", vars.get("var3"));
        assertEquals("three one two", vars.get("var4"));
    }

    @Test
    void testWithOverriddenVariables() throws Exception {
        String scriptType = UNIX_SCRIPT;
        String script = SCRIPT_OVERRIDDEN_VARIABLES_UNIX;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
            script = SCRIPT_OVERRIDDEN_VARIABLES_BATCH;
        }
        TestJob job = new TestJob(script, scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());

        EnvVars vars = job.build.getEnvironment(job.listener);
        assertEquals("not one" + File.pathSeparatorChar + "one", vars.get("var1"));
        assertEquals("two", vars.get("var2"));
    }

    @Test
    void testReadingFileFromSCM() throws Exception {
        String scriptType = UNIX_SCRIPT;
        String script = "cat envs";
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
            script = "type envs";
        }
        TestJob job = new TestJob(script, scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());
        assertEquals("bar", job.build.getEnvironment(job.listener).get("foo_var"));
    }

    @Test
    void testWorkingDirectory() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob("echo hi >was_run", scriptType, true);

        // Make sure that the $PWD of the script is $WORKSPACE.
        assertTrue(job.build.getWorkspace().child("was_run").exists());
    }

    @Test
    void testWithShebang() throws Exception {
        String scriptType = UNIX_SCRIPT;
        String script = SCRIPT_SHEBANG_UNIX;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
            script = SCRIPT_SHEBANG_BATCH;
        }
        TestJob job = new TestJob(script, scriptType, true);

        assertEquals(Result.SUCCESS, job.build.getResult());
        EnvVars vars = job.build.getEnvironment(job.listener);
        assertEquals("world", vars.get("hello"));
    }

    @Test
    void testUTFHandling() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob(SCRIPT_UTF8, scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());

        EnvVars vars = job.build.getEnvironment(job.listener);
        assertEquals("mąż", vars.get("UTFstr"));
    }

    @Test
    void testHideEnvironmentVariablesValues() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob(SCRIPT_SIMPLE_VARIABLES, scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());
        List<String> logs = job.build.getLog(10);

        assertTrue(logs.contains("[environment-script] Adding variable 'var1'"));
        assertTrue(logs.contains("[environment-script] Adding variable 'var2'"));
        assertTrue(logs.contains("[environment-script] Adding variable 'var3'"));
    }

    @Test
    void testShowEnvironmentVariablesValues() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob(SCRIPT_SIMPLE_VARIABLES, scriptType, false);
        assertEquals(Result.SUCCESS, job.build.getResult());
        List<String> logs = job.build.getLog(10);

        assertTrue(logs.contains("[environment-script] Adding variable 'var1' with value 'one'"));
        assertTrue(logs.contains("[environment-script] Adding variable 'var2' with value 'two'"));
        assertTrue(logs.contains("[environment-script] Adding variable 'var3' with value 'three'"));
    }
}
