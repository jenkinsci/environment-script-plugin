package com.lookout.jenkins;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.Charset;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.google.common.io.Files;

import hudson.FilePath;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixRun;
import hudson.matrix.DefaultMatrixExecutionStrategyImpl;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

public class EnvironmentScriptMultiMatrixTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    class MultiMatrixTestJob {
        public MatrixProject project;
        public CountBuilder countBuilder;
        public MatrixBuild build;
        public TaskListener listener;

        public MultiMatrixTestJob(String script, boolean onlyRunOnParent) throws Exception {
            listener = new StreamTaskListener(System.err, Charset.defaultCharset());
            project = jenkins.createProject(MatrixProject.class);

            // This forces it to run the builds sequentially, to prevent any
            // race conditions when concurrently updating the 'counter' file.
            project.setExecutionStrategy(new DefaultMatrixExecutionStrategyImpl(true, null, null, null));

            project.setAxes(new AxisList(new Axis("axis", "value1", "value2")));
            project.getBuildWrappersList()
                    .add(new EnvironmentScript(script, scriptType, onlyRunOnParent, hideGeneratedValue));
            countBuilder = new CountBuilder();
            project.getBuildersList().add(countBuilder);
            build = jenkins.buildAndAssertSuccess(project);
            jenkins.waitUntilNoActivity();
        }
    }

    final static String SCRIPT_COUNTER = "file='%s/counter'\n"
            + "if [ -f $file ]; then\n"
            + "  i=$(($(cat $file)+1))\n"
            + "else\n"
            + "  i=1\n"
            + "fi\n"
            + "echo 1 >was_run\n"
            + "echo $i >$file\n"
            + "echo seen=yes";

    // Generate a random directory that we pass to the shell script.
    File tempDir = Files.createTempDir();
    String script = String.format(SCRIPT_COUNTER, tempDir.getPath());
    String scriptType = "unixScript";
    boolean hideGeneratedValue = Boolean.TRUE;

    @Test
    public void testWithParentOnly() throws Exception {
        MultiMatrixTestJob job = new MultiMatrixTestJob(script, true);
        buildAndAssert(job);

        // We ensure that this was only run once (on the parent)
        assertEquals("1", new FilePath(tempDir).child("counter").readToString().trim());

        // Then make sure that it was in fact in the parent's WS that we ran.
        assertTrue(job.build.getWorkspace().child("was_run").exists());
        for (MatrixRun run : job.build.getRuns())
            assertFalse(run.getWorkspace().child("was_run").exists());
    }

    @Test
    public void testWithEachChild() throws Exception {
        MultiMatrixTestJob job = new MultiMatrixTestJob(script, false);

        // We ensure that this was only run twice - once for each axis combination - but
        // not on the parent.
        assertEquals("2", new FilePath(tempDir).child("counter").readToString().trim());

        // Then make sure that it was in fact in the combination jobs' workspace.
        assertFalse(job.build.getWorkspace().child("was_run").exists());
        for (MatrixRun run : job.build.getRuns())
            assertTrue(run.getWorkspace().child("was_run").exists());
    }

    private void buildAndAssert(MultiMatrixTestJob job) throws Exception {
        assertEquals(Result.SUCCESS, job.build.getResult());

        // Make sure that the environment variables set in the script are properly
        // propagated.
        assertEquals("yes", job.build.getEnvironment(job.listener).get("seen"));
        // Make sure that the builder was executed twice, once for each axis value.
        assertEquals(2, job.countBuilder.getCount());
    }
}
