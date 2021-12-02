package com.lookout.jenkins.commands;

import java.util.ArrayList;
import java.util.List;

import hudson.FilePath;

public class PowerShell {

    // Mostly stolen from managed-scripts-plugin
    public static String[] buildCommandLine(FilePath scriptFile) {
        List<String> cml = new ArrayList<String>();
        cml.add("powershell.exe");
        cml.add("-ExecutionPolicy");
        cml.add("ByPass");
        cml.add("& \'" + scriptFile.getRemote() + "\'");

        return (String[]) cml.toArray(new String[cml.size()]);
    }

}
