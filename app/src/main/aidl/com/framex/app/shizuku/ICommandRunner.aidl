package com.framex.app.shizuku;

interface ICommandRunner {
    String executeCommand(String command);
    int executeCommandWithExitCode(String command);
    String getThermalTemperatures();
    int suspendPackages(in String[] packageNames, boolean suspended);
    int setAppOpMode(in String[] packageNames, int opCode, int mode);
    void destroy();
}
