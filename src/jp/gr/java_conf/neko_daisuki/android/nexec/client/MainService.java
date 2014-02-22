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
import android.os.RemoteException;
import android.util.JsonReader;
import android.util.Log;
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
            Log.v(LOG_TAG, message);
        }

        public void debug(String message) {
            Log.d(LOG_TAG, message);
        }

        public void info(String message) {
            Log.i(LOG_TAG, message);
        }

        public void warn(String message) {
            Log.w(LOG_TAG, message);
        }

        public void err(String message) {
            Log.e(LOG_TAG, message);
        }
    }

    private class Stub extends INexecService.Stub {

        private class Task extends AsyncTask<Void, Void, Void> {

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
                    try {
                        mCallback.writeStdout(b);
                    }
                    catch (RemoteException e) {
                        showException("write error for stdout", e);
                    }
                }
            }

            private class Stderr extends OutputStream {

                @Override
                public void write(int b) throws IOException {
                    try {
                        mCallback.writeStderr(b);
                    }
                    catch (RemoteException e) {
                        showException("write error for stderr", e);
                    }
                }
            }

            private SessionParameter mSessionParameter;

            private INexecCallback mCallback;
            private InputStream mStdin = new Input();
            private OutputStream mStdout = new Stdout();
            private OutputStream mStderr = new Stderr();

            public Task(SessionParameter param) {
                mSessionParameter = param;
            }

            public void setCallback(INexecCallback callback) {
                mCallback = callback;
            }

            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);
                Log.i(LOG_TAG, "The task finished.");
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
                            mSessionParameter.env, perm,
                            mSessionParameter.links);
                    mCallback.exit(exitCode);
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
                catch (RemoteException e) {
                    showException("socket error", e);
                }
            }

            private void showException(String msg, Exception e) {
                e.printStackTrace();

                String s = String.format("%s: %s", msg, e.getMessage());
                mHandler.post(new ExceptionProcessor(s));
            }
        }

        private class Session {

            private Task mTask;

            public Session(Task task) {
                mTask = task;
            }

            public Task getTask() {
                return mTask;
            }
        }

        private class Sessions {

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

        private class SessionParameter {

            public String host = "";
            public int port;
            public String[] args = new String[0];
            public Environment env;
            public String[] files = new String[0];
            public Links links;
        }

        private Sessions mSessions = new Sessions();

        @Override
        public void execute(SessionId sessionId, INexecCallback callback) throws RemoteException {
            Log.i(LOG_TAG, String.format("execute: %s", sessionId));
            startTask(sessionId, callback);
        }

        @Override
        public void connect(SessionId sessionId, INexecCallback callback)
                throws RemoteException {
            Log.i(LOG_TAG, String.format("connect: %s", sessionId));
            Session session = mSessions.get(sessionId);
            if (session == null) {
                return;
            }

            session.getTask().setCallback(callback);
        }

        @Override
        public void disconnect(SessionId sessionId) throws RemoteException {
            Log.i(LOG_TAG, String.format("disconnected: %s", sessionId));
            Session session = mSessions.get(sessionId);
            if (session == null) {
                return;
            }

            session.getTask().setCallback(null);
        }

        @Override
        public void quit(SessionId sessionId) throws RemoteException {
            Log.i(LOG_TAG, String.format("quit: %s", sessionId));
            Session session = mSessions.get(sessionId);
            if (session == null) {
                return;
            }

            session.getTask().cancel(true);
            mSessions.remove(sessionId);
        }

        @Override
        public void writeStdin(SessionId sessionId, int b) throws RemoteException {
            // TODO Auto-generated method stub
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

        private void startTask(SessionId sessionId, INexecCallback callback) {
            SessionParameter param;
            try {
                param = readSessionParameter(sessionId);
            }
            catch (IOException e) {
                String fmt = "failed to read session parameter: %s: %s";
                showToast(String.format(fmt, sessionId, e.getMessage()));
                e.printStackTrace();
                return;
            }
            if (!removeSessionFile(sessionId)) {
                return;
            }

            Task task = new Task(param);
            task.setCallback(callback);
            task.execute();
            String fmt = "A new task started for session %s.";
            Log.i(LOG_TAG, String.format(fmt, sessionId.toString()));

            mSessions.put(sessionId, new Session(task));
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
    }

    private static final String LOG_TAG = "nexec client service";

    private Handler mHandler = new Handler();

    public void onCreate() {
        super.onCreate();
        Logging.setDestination(new Destination());
        Log.i(LOG_TAG, "MainService was created.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Stub();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

// vim: tabstop=4 shiftwidth=4 expandtab softtabstop=4
