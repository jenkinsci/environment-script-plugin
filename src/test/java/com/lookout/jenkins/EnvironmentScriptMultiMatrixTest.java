package com.lookout.jenkins;

import java.io.File;
import java.nio.charset.Charset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.file.Files;

import hudson.FilePath;

import static org.junit.jupiter.api.Assertions.*;
import hudson.Functions;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixRun;
import hudson.matrix.DefaultMatrixExecutionStrategyImpl;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class EnvironmentScriptMultiMatrixTest {

    private JenkinsRule jenkins;

    class MultiMatrixTestJob {
        private final MatrixProject project;
        private final CountBuilder countBuilder;
        private final MatrixBuild build;
        private final TaskListener listener;

        MultiMatrixTestJob(String script, boolean onlyRunOnParent) throws Exception {
            listener = new StreamTaskListener(System.err, Charset.defaultCharset());
            project = jenkins.createProject(MatrixProject.class);

            // This forces it to run the builds sequentially, to prevent any
            // race conditions when concurrently updating the 'counter' file.
            project.setExecutionStrategy(new DefaultMatrixExecutionStrategyImpl(true, null, null, null));

            String scriptType = UNIX_SCRIPT;
            if (Functions.isWindows()) {
                scriptType = BATCH_SCRIPT;
            }

            project.setAxes(new AxisList(new Axis("axis", "value1", "value2")));
            project.getBuildWrappersList()
                    .add(new EnvironmentScript(script, scriptType, onlyRunOnParent, hideGeneratedValue));
            countBuilder = new CountBuilder();
            project.getBuildersList().add(countBuilder);
            build = jenkins.buildAndAssertSuccess(project);
            jenkins.waitUntilNoActivity();
        }
    }

    private final static String SCRIPT_COUNTER_UNIX = """
        file='%s/counter'
        if [ -f $file ]; then
          i=$(($(cat $file)+1))
        else
          i=1
        fi
        echo 1 > was_run
        echo $i > $file
        echo seen=yes""";

    private final static String SCRIPT_COUNTER_BATCH = """
        @echo off
        set file=%s\\counter
        if exist %%file%% (
          set /p i=< %%file%%
          set /a i+=1
        ) else (
          set i=1
        )
        echo 1 > was_run
        echo %%i%% > %%file%%
        echo seen=yes""";

    // Generate a random directory that we pass to the shell script.
    private File tempDir;

    private String script;
    private final static String UNIX_SCRIPT = "unixScript";
    private final static String BATCH_SCRIPT = "batchScript";
    private final boolean hideGeneratedValue = Boolean.TRUE;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        jenkins = rule;
        // Generate a random directory that we pass to the shell script.
        tempDir = Files.createTempDirectory("tmp").toFile();
        String scriptCounter = SCRIPT_COUNTER_UNIX;
        if (Functions.isWindows()) {
            scriptCounter = SCRIPT_COUNTER_BATCH;
        }
        script = String.format(scriptCounter, tempDir);
    }

    @Test
    void testWithParentOnly() throws Exception {
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
    void testWithEachChild() throws Exception {
        MultiMatrixTestJob job = new MultiMatrixTestJob(script, false);

        // We ensure that this was only run twice - once for each axis combination - but
        // not on the parent.
        assertEquals("2", new FilePath(tempDir).child("counter").readToString().trim());

        // Then make sure that it was in fact in the combination jobs' workspace.
        assertFalse(job.build.getWorkspace().child("was_run").exists());
        for (MatrixRun run : job.build.getRuns())
            assertTrue(run.getWorkspace().child("was_run").exists());
    }

    private static void buildAndAssert(MultiMatrixTestJob job) throws Exception {
        assertEquals(Result.SUCCESS, job.build.getResult());

        // Make sure that the environment variables set in the script are properly
        // propagated.
        assertEquals("yes", job.build.getEnvironment(job.listener).get("seen"));
        // Make sure that the builder was executed twice, once for each axis value.
        assertEquals(2, job.countBuilder.getCount());
    }
}
