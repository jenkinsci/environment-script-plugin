package com.lookout.jenkins;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

import hudson.EnvVars;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.TaskListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.StreamTaskListener;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

public class EnvironmentScriptTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    class TestJob {
        public FreeStyleProject project;
        public FreeStyleBuild build;
        public TaskListener listener;

        public TestJob(String script, String scriptType, boolean hideEnvironmentVariablesValues) throws Exception {
            listener = new StreamTaskListener(System.err, Charset.defaultCharset());
            project = jenkins.createFreeStyleProject();
            project.getBuildWrappersList()
                    .add(new EnvironmentScript(script, scriptType, false, hideEnvironmentVariablesValues));
            project.setScm(new SingleFileSCM("envs", "foo_var=bar"));
            build = jenkins.buildAndAssertSuccess(project);
            jenkins.waitUntilNoActivity();
        }
    }

    final static String UNIX_SCRIPT = "unixScript";
    final static String BATCH_SCRIPT = "batchScript";

    final static String SCRIPT_SIMPLE_VARIABLES = "echo var1=one\n"
            + "echo var2=two\n"
            + "echo var3=three";

    final static String SCRIPT_DEPENDENT_VARIABLES_UNIX = "echo var1=one\n"
            + "echo var2='$var1 two'\n"
            + "echo var3='yo $var4'\n"
            + "echo var4='three ${var2}'";

    final static String SCRIPT_DEPENDENT_VARIABLES_BATCH = "echo var1=one\n"
            + "echo var2=$var1 two\n"
            + "echo var3=yo $var4\n"
            + "echo var4=three ${var2}";

    final static String SCRIPT_OVERRIDDEN_VARIABLES_UNIX = "echo var1=one\n"
            + "echo var1+something='not one'\n"
            + "echo var2+something='two'";

    final static String SCRIPT_OVERRIDDEN_VARIABLES_BATCH = "echo var1=one\n"
            + "echo var1+something=not one\n"
            + "echo var2+something=two";

    final static String SCRIPT_UTF8 = "echo UTFstr=mąż";

    final static String SCRIPT_SHEBANG_UNIX = "#!/bin/cat\n"
            + "hello=world";

    // batch script does not have shebang
    final static String SCRIPT_SHEBANG_BATCH = "echo hello=world";

    public void testWithEmptyScript() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob("", scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());
    }

    @Test
    public void testWithSimpleVariables() throws Exception {
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
    public void testWithDependentVariables() throws Exception {
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
    public void testWithOverriddenVariables() throws Exception {
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
    public void testReadingFileFromSCM() throws Exception {
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
    public void testWorkingDirectory() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob("echo hi >was_run", scriptType, true);

        // Make sure that the $PWD of the script is $WORKSPACE.
        assertTrue(job.build.getWorkspace().child("was_run").exists());
    }

    @Test
    public void testWithShebang() throws Exception {
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

    public void testUTFHandling() throws Exception {
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
    public void testHideEnvironmentVariablesValues() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob(SCRIPT_SIMPLE_VARIABLES, scriptType, true);
        assertEquals(Result.SUCCESS, job.build.getResult());
        List<String> logs = job.build.getLog(10);

        assertTrue(logs.contains(new String("[environment-script] Adding variable 'var1'")));
        assertTrue(logs.contains(new String("[environment-script] Adding variable 'var2'")));
        assertTrue(logs.contains(new String("[environment-script] Adding variable 'var3'")));
    }

    @Test
    public void testShowEnvironmentVariablesValues() throws Exception {
        String scriptType = UNIX_SCRIPT;
        if (Functions.isWindows()) {
            scriptType = BATCH_SCRIPT;
        }
        TestJob job = new TestJob(SCRIPT_SIMPLE_VARIABLES, scriptType, false);
        assertEquals(Result.SUCCESS, job.build.getResult());
        List<String> logs = job.build.getLog(10);

        assertTrue(logs.contains(new String("[environment-script] Adding variable 'var1' with value 'one'")));
        assertTrue(logs.contains(new String("[environment-script] Adding variable 'var2' with value 'two'")));
        assertTrue(logs.contains(new String("[environment-script] Adding variable 'var3' with value 'three'")));
    }
}
