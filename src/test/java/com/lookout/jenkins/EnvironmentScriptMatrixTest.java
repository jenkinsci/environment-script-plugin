package com.lookout.jenkins;

import java.io.File;

import hudson.FilePath;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixRun;
import hudson.matrix.DefaultMatrixExecutionStrategyImpl;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;

import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

public class EnvironmentScriptMatrixTest extends HudsonTestCase {
	class MatrixTestJob {
		public MatrixProject project;
		public CaptureEnvironmentBuilder captureBuilder;
		public CountBuilder countBuilder;

		public MatrixTestJob (String script, boolean onlyRunOnParent) throws Exception {
			project = createMatrixProject();

			// This forces it to run the builds sequentially, to prevent any
			// race conditions when concurrently updating the 'counter' file.
			project.setExecutionStrategy(new DefaultMatrixExecutionStrategyImpl(true, null, null, null));

			project.setAxes(new AxisList(new Axis("axis", "value1", "value2")));
			project.getBuildWrappersList().add(new EnvironmentScript(script, onlyRunOnParent));

			captureBuilder = new CaptureEnvironmentBuilder();
			project.getBuildersList().add(captureBuilder);

			countBuilder = new CountBuilder();
			project.getBuildersList().add(countBuilder);
		}
	}

	final static String SCRIPT_COUNTER =
		"file='%s/counter'\n"
		+ "if [ -f $file ]; then\n"
		+ "  let i=$(cat $file)+1\n"
		+ "else\n"
		+ "  i=1\n"
		+ "fi\n"
		+ "echo 1 >was_run\n"
		+ "echo $i >$file\n"
		+ "echo seen=yes";


	// Explicit constructor so that we can call createTmpDir.
	public EnvironmentScriptMatrixTest () throws Exception {}

	// Generate a random directory that we pass to the shell script.
	File tempDir = createTmpDir();
	String script = String.format(SCRIPT_COUNTER, tempDir.getPath());

	public void testWithParentOnly () throws Exception {
		MatrixTestJob job = new MatrixTestJob(script, true);
		MatrixBuild build = buildAndAssert(job);

		// We ensure that this was only run once (on the parent)
		assertEquals("1", new FilePath(tempDir).child("counter").readToString().trim());

		// Then make sure that it was in fact in the parent's WS that we ran.
		assertTrue(build.getWorkspace().child("was_run").exists());
		for (MatrixRun run : build.getRuns())
			assertFalse(run.getWorkspace().child("was_run").exists());
	}

	public void testWithEachChild () throws Exception {
		MatrixTestJob job = new MatrixTestJob(script, false);
		MatrixBuild build = buildAndAssert(job);

		// We ensure that this was only run twice - once for each axis combination - but not on the parent.
		assertEquals("2", new FilePath(tempDir).child("counter").readToString().trim());

		// Then make sure that it was in fact in the combination jobs' workspace.
		assertFalse(build.getWorkspace().child("was_run").exists());
		for (MatrixRun run : build.getRuns())
			assertTrue(run.getWorkspace().child("was_run").exists());
	}

	private MatrixBuild buildAndAssert(MatrixTestJob job) throws Exception {
		MatrixBuild build = assertBuildStatusSuccess(job.project.scheduleBuild2(0).get());

		// Make sure that the environment variables set in the script are properly propagated.
		assertEquals("yes", job.captureBuilder.getEnvVars().get("seen"));
		// Make sure that the builder was executed twice, once for each axis value.
		assertEquals(2, job.countBuilder.getCount());

		return build;
	}
}
