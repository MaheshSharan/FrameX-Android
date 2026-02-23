package com.framex.app.shizuku;

interface ICommandRunner {
    String executeCommand(String command);
    void destroy();
}
