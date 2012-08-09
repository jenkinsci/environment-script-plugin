package com.lookout.jenkins;

import hudson.EnvVars;
import hudson.model.Build;
import hudson.model.Project;

import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;

public class EnvironmentScriptTest extends HudsonTestCase {
	class TestJob {
		public Project<?,?> project;
		public CaptureEnvironmentBuilder builder;

		public TestJob (String script) throws Exception {
			project = createFreeStyleProject();
			builder = new CaptureEnvironmentBuilder();
			project.getBuildersList().add(builder);
			project.getBuildWrappersList().add(new EnvironmentScript(script));
		}
	}

	public void testWithEmptyScript () throws Exception {
		TestJob job = new TestJob("");
		assertBuildStatusSuccess(job.project.scheduleBuild2(0).get());
	}

	final static String SCRIPT_SIMPLE_VARIABLES =
		"echo var1=one\n"
		+ "echo var2=two\n"
		+ "echo var3=three";
	public void testWithSimpleVariables () throws Exception {
		TestJob job = new TestJob(SCRIPT_SIMPLE_VARIABLES);
		assertBuildStatusSuccess(job.project.scheduleBuild2(0).get());

		EnvVars vars = job.builder.getEnvVars();
		assertEquals("one", vars.get("var1"));
		assertEquals("two", vars.get("var2"));
		assertEquals("three", vars.get("var3"));
	}

	final static String SCRIPT_DEPENDENT_VARIABLES =
		"echo var1=one\n"
		+ "echo var2='$var1 two'\n"
		+ "echo var3='yo $var4'\n"
		+ "echo var4='three ${var2}'";
	public void testWithDependentVariables () throws Exception {
		TestJob job = new TestJob(SCRIPT_DEPENDENT_VARIABLES);
		assertBuildStatusSuccess(job.project.scheduleBuild2(0).get());

		EnvVars vars = job.builder.getEnvVars();
		assertEquals("one", vars.get("var1"));
		assertEquals("one two", vars.get("var2"));
		assertEquals("yo three ${var2}", vars.get("var3"));
		assertEquals("three one two", vars.get("var4"));
	}

	final static String SCRIPT_OVERRIDDEN_VARIABLES =
		"echo var1=one\n"
		+ "echo var1+something='not one'\n"
		+ "echo var2+something='two'";
	public void testWithOverridenVariables () throws Exception {
		TestJob job = new TestJob(SCRIPT_OVERRIDDEN_VARIABLES);
		assertBuildStatusSuccess(job.project.scheduleBuild2(0).get());

		EnvVars vars = job.builder.getEnvVars();
		assertEquals("not one:one", vars.get("var1"));
		assertEquals("two", vars.get("var2"));
	}

	public void testReadingFileFromSCM () throws Exception {
		TestJob job = new TestJob("cat envs");
		job.project.setScm(new SingleFileSCM("envs", "foo_var=bar"));

		assertBuildStatusSuccess(job.project.scheduleBuild2(0).get());
		assertEquals("bar", job.builder.getEnvVars().get("foo_var"));
	}

	public void testWorkingDirectory () throws Exception {
		TestJob job = new TestJob("echo hi >was_run");
		Build<?, ?> build = assertBuildStatusSuccess(job.project.scheduleBuild2(0).get());

		// Make sure that the $PWD of the script is $WORKSPACE.
		assertTrue(build.getWorkspace().child("was_run").exists());
	}
}
