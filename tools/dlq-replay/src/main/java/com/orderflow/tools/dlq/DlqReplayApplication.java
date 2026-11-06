package com.orderflow.tools.dlq;

public final class DlqReplayApplication {

    private DlqReplayApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new DlqReplayCommand().run(args, System.getenv(), System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
