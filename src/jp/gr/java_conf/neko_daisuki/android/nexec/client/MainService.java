package jp.gr.java_conf.neko_daisuki.android.nexec.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.JsonReader;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import jp.gr.java_conf.neko_daisuki.fsyscall.Logging;
import jp.gr.java_conf.neko_daisuki.fsyscall.slave.Links;
import jp.gr.java_conf.neko_daisuki.fsyscall.slave.Permissions;
import jp.gr.java_conf.neko_daisuki.nexec.client.NexecClient.Environment;
import jp.gr.java_conf.neko_daisuki.nexec.client.NexecClient;
import jp.gr.java_conf.neko_daisuki.nexec.client.ProtocolException;

public class MainService extends Service {

    private static class Destination implements Logging.Destination {

        public void verbose(String message) {
            Log.v(TAG, message);
        }

        public void debug(String message) {
            Log.d(TAG, message);
        }

        public void info(String message) {
            Log.i(TAG, message);
        }

        public void warn(String message) {
            Log.w(TAG, message);
        }

        public void err(String message) {
            Log.e(TAG, message);
        }
    }

    private class Task extends AsyncTask<Void, Void, Void> {

        private abstract class MessengerWrapper {

            public abstract void send(Message msg);

            public void sendIntMessage(int what, int arg1) {
                send(Message.obtain(null, what, arg1, 0));
            }
        }

        private class TrueMessenger extends MessengerWrapper {

            private Messenger mMessenger;

            public TrueMessenger(Messenger messenger) {
                mMessenger = messenger;
            }

            @Override
            public void send(Message msg) {
                try {
                    mMessenger.send(msg);
                }
                catch (RemoteException e) {
                    showException("Cannot send message", e);
                }
            }
        }

        private class FakeMessenger extends MessengerWrapper {

            @Override
            public void send(Message msg) {
            }
        }

        private class ExceptionProcessor implements Runnable {

            private String mMessage;

            public ExceptionProcessor(String message) {
                mMessage = message;
            }

            public void run() {
                showToast(mMessage);
            }
        }

        private class Input extends InputStream {

            @Override
            public int read() throws IOException {
                // TODO
                return 0;
            }
        }

        private class Stdout extends OutputStream {

            @Override
            public void write(int b) throws IOException {
                mMessenger.sendIntMessage(MessageWhat.MSG_STDOUT, b);
            }
        }

        private class Stderr extends OutputStream {

            @Override
            public void write(int b) throws IOException {
                mMessenger.sendIntMessage(MessageWhat.MSG_STDERR, b);
            }
        }

        private final MessengerWrapper FAKE_MESSENGER = new FakeMessenger();

        private SessionParameter mSessionParameter;

        private MessengerWrapper mMessenger = FAKE_MESSENGER;
        private InputStream mStdin = new Input();
        private OutputStream mStdout = new Stdout();
        private OutputStream mStderr = new Stderr();

        public Task(SessionParameter param) {
            mSessionParameter = param;
        }

        public void setMessengerToClient(Messenger messenger) {
            mMessenger = messenger != null
                    ? new TrueMessenger(messenger)
                    : FAKE_MESSENGER;
        }

        protected Void doInBackground(Void... params) {
            run();
            return null;
        }

        private void run() {
            Permissions perm = new Permissions();
            for (String path: mSessionParameter.files) {
                perm.allowPath(path);
            }

            NexecClient nexec = new NexecClient();
            try {
                int exitCode = nexec.run(
                        mSessionParameter.host, mSessionParameter.port,
                        mSessionParameter.args, mStdin, mStdout, mStderr,
                        mSessionParameter.env, perm, mSessionParameter.links);
                mMessenger.sendIntMessage(MessageWhat.MSG_EXIT, exitCode);
            }
            catch (ProtocolException e) {
                showException("protocol error", e);
            }
            catch (InterruptedException e) {
                showException("interrupted", e);
            }
            catch (IOException e) {
                showException("I/O error", e);
            }
        }

        private void showException(String msg, Exception e) {
            e.printStackTrace();

            String s = String.format("%s: %s", msg, e.getMessage());
            mHandler.post(new ExceptionProcessor(s));
        }
    }

    private static class IncomingHandler extends Handler {

        private interface MessageHandler {

            public void handle(Message msg);
        }

        private class ConnectHandler implements MessageHandler {

            public void handle(Message msg) {
                mTask.setMessengerToClient(msg.replyTo);
            }
        }

        private class DisconnectHandler implements MessageHandler {

            @Override
            public void handle(Message msg) {
                mTask.setMessengerToClient(null);
            }
        }

        private class QuitHandler implements MessageHandler {

            @Override
            public void handle(Message msg) {
                mTask.cancel(true);
                mTask.setMessengerToClient(null);
                mQuitCallback.quit();
            }
        }

        // documents
        private Task mTask;
        private QuitCallback mQuitCallback;

        // helpers
        private SparseArray<MessageHandler> mHandlers;

        public IncomingHandler(Task task, QuitCallback quitCallback) {
            mTask = task;
            mQuitCallback = quitCallback;

            mHandlers = new SparseArray<MessageHandler>();
            mHandlers.put(MessageWhat.MSG_CONNECT, new ConnectHandler());
            mHandlers.put(MessageWhat.MSG_DISCONNECT, new DisconnectHandler());
            mHandlers.put(MessageWhat.MSG_QUIT, new QuitHandler());
        }

        public void handleMessage(Message msg) {
            mHandlers.get(msg.what).handle(msg);
        }
    }

    private static class SessionParameter {

        public String host = "";
        public int port;
        public String[] args = new String[0];
        public Environment env;
        public String[] files = new String[0];
        public Links links;
    }

    private static class Session {

        private Task mTask;

        public Session(Task task) {
            mTask = task;
        }

        public Task getTask() {
            return mTask;
        }
    }

    private static class SessionId {

        private String mId;

        public SessionId(String id) {
            mId = id;
        }

        @Override
        public boolean equals(Object o) {
            try {
                return ((SessionId)o).toString().equals(mId);
            }
            catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return mId.hashCode();
        }

        @Override
        public String toString() {
            return mId;
        }
    }

    private static class Sessions {

        private Map<SessionId, Session> mMap;

        public Sessions() {
            mMap = new ConcurrentHashMap<SessionId, Session>();
        }

        public void put(SessionId sessionId, Session session) {
            mMap.put(sessionId, session);
        }

        public Session get(SessionId sessionId) {
            return mMap.get(sessionId);
        }

        public void remove(SessionId sessionId) {
            mMap.remove(sessionId);
        }
    }

    private class QuitCallback {

        private SessionId mSessionId;

        public QuitCallback(SessionId sessionId) {
            mSessionId = sessionId;
        }

        public void quit() {
            mSessions.remove(mSessionId);
        }
    }

    private static final String TAG = "nexec client";

    private Sessions mSessions = new Sessions();
    private Handler mHandler = new Handler();

    public void onCreate() {
        super.onCreate();
        Logging.setDestination(new Destination());
        Log.i(TAG, "MainService was created.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        String key = "SESSION_ID";
        SessionId sessionId = new SessionId(intent.getStringExtra(key));
        Task task = getTask(sessionId);
        return task != null ? createBinder(task, sessionId) : null;
    }

    private IBinder createBinder(Task task, SessionId sessionId) {
        QuitCallback quitCallback = new QuitCallback(sessionId);
        Handler handler = new IncomingHandler(task, quitCallback);
        return new Messenger(handler).getBinder();
    }

    private Task getTask(SessionId sessionId) {
        Session session = mSessions.get(sessionId);
        return session != null ? session.getTask() : createTask(sessionId);
    }

    private Task createTask(SessionId sessionId) {
        SessionParameter param;
        try {
            param = readSessionParameter(sessionId);
        }
        catch (IOException e) {
            String fmt = "failed to read session parameter: %s: %s";
            showToast(String.format(fmt, sessionId, e.getMessage()));
            e.printStackTrace();
            return null;
        }
        if (!removeSessionFile(sessionId)) {
            return null;
        }

        Task task = new Task(param);
        task.execute();

        mSessions.put(sessionId, new Session(task));

        return task;
    }

    private boolean removeSessionFile(SessionId sessionId) {
        String dir = getFilesDir().getAbsolutePath();
        String path = String.format("%s/%s", dir, sessionId.toString());
        boolean result = new File(path).delete();
        if (!result) {
            showToast(String.format("failed to delete %s.", path));
        }
        return result;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private Links readLinks(JsonReader reader) throws IOException {
        Links links = new Links();

        reader.beginArray();
        while (reader.hasNext()) {
            String dest = null;
            String src = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("dest")) {
                    dest = reader.nextString();
                }
                else if (name.equals("src")) {
                    src = reader.nextString();
                }
            }
            reader.endObject();

            links.put(dest, src);
        }
        reader.endArray();

        return links;
    }

    private String[] readArray(JsonReader reader) throws IOException {
        List<String> list = new LinkedList<String>();

        reader.beginArray();
        while (reader.hasNext()) {
            list.add(reader.nextString());
        }
        reader.endArray();

        return list.toArray(new String[0]);
    }

    private Environment readEnvironment(JsonReader reader) throws IOException {
        Environment env = new Environment();

        reader.beginArray();
        while (reader.hasNext()) {
            String key = null;
            String value = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("name")) {
                    key = reader.nextString();
                }
                else if (name.equals("value")) {
                    value = reader.nextString();
                }
            }
            reader.endObject();

            if ((key != null) && (value != null)) {
                env.put(key, value);
            }
        }
        reader.endArray();

        return env;
    }

    private SessionParameter readSessionParameter(SessionId sessionId) throws IOException {
        SessionParameter param = new SessionParameter();

        JsonReader reader = new JsonReader(
                new InputStreamReader(openFileInput(sessionId.toString())));
        try {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("host")) {
                    param.host = reader.nextString();
                }
                else if (name.equals("port")) {
                    param.port = reader.nextInt();
                }
                else if (name.equals("args")) {
                    param.args = readArray(reader);
                }
                else if (name.equals("env")) {
                    param.env = readEnvironment(reader);
                }
                else if (name.equals("files")) {
                    param.files = readArray(reader);
                }
                else if (name.equals("links")) {
                    param.links = readLinks(reader);
                }
            }
            reader.endObject();
        }
        finally {
            reader.close();
        }

        return param;
    }
}

// vim: tabstop=4 shiftwidth=4 expandtab softtabstop=4
