package com.framex.app.shizuku;

import com.framex.app.shizuku.CommandResult;

interface ICommandRunner {
    String executeCommand(String command);
    int executeCommandWithExitCode(String command);
    CommandResult executeCommandWithResult(String command);
    String readProcStat();
    String getThermalTemperatures();
    int suspendPackages(in String[] packageNames, boolean suspended);
    int setAppOpMode(in String[] packageNames, int opCode, int mode);
    void destroy();
}
