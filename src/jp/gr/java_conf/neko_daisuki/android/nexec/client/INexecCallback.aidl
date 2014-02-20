package jp.gr.java_conf.neko_daisuki.android.nexec.client;

interface INexecCallback {

    oneway void writeStdout(int b);
    oneway void writeStderr(int b);
}