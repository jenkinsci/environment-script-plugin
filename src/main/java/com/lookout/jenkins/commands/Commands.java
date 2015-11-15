package com.lookout.jenkins.commands;

public class Commands {

    public final static String POWER_SHELL = "powerShell";
    public final static String BATCH_SCRIPT = "batchScript";

    public static boolean isShebangs(String script) {
        if (script.startsWith("#!")) {
            return true;
        }
        return false;
    }

}
