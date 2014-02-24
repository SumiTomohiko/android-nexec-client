package jp.gr.java_conf.neko_daisuki.android.nexec.client;

interface INexecCallback {

    oneway void exit(int status);
    oneway void writeStdout(in byte[] buf);
    oneway void writeStderr(in byte[] buf);
}