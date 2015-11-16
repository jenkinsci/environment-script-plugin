package com.lookout.jenkins.commands;

import java.util.ArrayList;
import java.util.List;

import hudson.FilePath;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;

public class UnixShell {

    public static String[] buildCommandLine(FilePath scriptFile) {
        Shell.DescriptorImpl shellDescriptor = Jenkins.getInstance()
                .getDescriptorByType(Shell.DescriptorImpl.class);
        final String shell = shellDescriptor.getShellOrDefault(scriptFile.getChannel());

        List<String> cml = new ArrayList<String>();
        cml.add(shell);
        cml.add("-xe");
        cml.add(scriptFile.getRemote());

        return (String[]) cml.toArray(new String[cml.size()]);
    }

}
