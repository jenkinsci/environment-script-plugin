package com.lookout.jenkins.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import hudson.FilePath;
import hudson.Util;

public class Shebangs {

    public static String[] parseCommandLine(String script, FilePath scriptFile) {
        // Find first line, or just entire script if it's one line.
        int end = script.indexOf('\n');
        if (end < 0)
            end = script.length();

        String interpreter = script.substring(0, end).trim();
        interpreter = interpreter.substring(2);

        List<String> cml = new ArrayList<String>(Arrays.asList(Util.tokenize(interpreter)));
        cml.add(scriptFile.getRemote());

        return cml.toArray(new String[cml.size()]);
    }
}
