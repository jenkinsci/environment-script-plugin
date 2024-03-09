package com.lookout.jenkins.commands;

public class Commands {

    public final static String UNIX_SCRIPT = "unixScript";
    public final static String UNIX_SCRIPT_DISPLAY_NAME = "Unix script";

    public final static String POWER_SHELL = "powerShell";
    public final static String POWER_SHELL_DISPLAY_NAME = "Powershell script";

    public final static String BATCH_SCRIPT = "batchScript";
    public final static String BATCH_SCRIPT_DISPLAY_NAME = "Batch script";

    public static boolean isShebangs(String script) {
        if (script.startsWith("#!")) {
            return true;
        }
        return false;
    }

}
