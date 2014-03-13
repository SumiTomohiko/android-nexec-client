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
                    // TODO: Write stdin.
                    return 0;
                }
            }

            private abstract class Output extends OutputStream {

                private String mName;

                public Output(String name) {
                    mName = name;
                }

                @Override
                public void write(byte[] buffer) throws IOException {
                    try {
                        callback(buffer);
                    }
                    catch (RemoteException e) {
                        String fmt = "write error for %s";
                        handleException(String.format(fmt, mName), e);
                    }
                }

                @Override
                public void write(int b) throws IOException {
                    write(new byte[] { (byte)b });
                }

                protected abstract void callback(byte[] buffer) throws RemoteException;
            }

            private class Stdout extends Output {

                public Stdout() {
                    super("stdout");
                }

                @Override
                protected void callback(byte[] buffer) throws RemoteException {
                    mCallback.writeStdout(buffer);
                }
            }

            private class Stderr extends Output {

                public Stderr() {
                    super("stderr");
                }

                @Override
                protected void callback(byte[] buffer) throws RemoteException {
                    mCallback.writeStderr(buffer);
                }
            }

            private abstract class Callback {

                public abstract void writeStdout(byte[] buf) throws RemoteException;
                public abstract void writeStderr(byte[] buf) throws RemoteException;
                public abstract void exit(int status) throws RemoteException;
            }

            private class NopCallback extends Callback {

                @Override
                public void writeStdout(byte[] buf)  throws RemoteException{
                }

                @Override
                public void writeStderr(byte[] buf)  throws RemoteException{
                }

                @Override
                public void exit(int status)  throws RemoteException{
                }
            }

            private class TrueCallback extends Callback {

                private INexecCallback mCallback;

                public TrueCallback(INexecCallback callback) {
                    mCallback = callback;
                }

                @Override
                public void writeStdout(byte[] buf) throws RemoteException {
                    mCallback.writeStdout(buf);
                }

                @Override
                public void writeStderr(byte[] buf) throws RemoteException {
                    mCallback.writeStderr(buf);
                }

                @Override
                public void exit(int status) throws RemoteException {
                    mCallback.exit(status);
                }
            }

            private SessionParameter mSessionParameter;

            private Callback mCallback;
            private InputStream mStdin = new Input();
            private OutputStream mStdout = new Stdout();
            private OutputStream mStderr = new Stderr();

            public Task(SessionParameter param) {
                mSessionParameter = param;
            }

            public void setCallback(INexecCallback callback) {
                mCallback = callback != null ? new TrueCallback(callback)
                                             : new NopCallback();
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
                    handleException("protocol error", e);
                }
                catch (InterruptedException e) {
                    handleException("interrupted", e);
                }
                catch (IOException e) {
                    handleException("I/O error", e);
                }
                catch (RemoteException e) {
                    handleException("socket error", e);
                }
            }

            private void handleException(String msg, Exception e) {
                setCallback(null);
                e.printStackTrace();

                String fmt = "nexec service: %s: %s: %s";
                String name = e.getClass().getName();
                String s = String.format(fmt, msg, name, e.getMessage());
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
            // TODO: Write stdin.
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
                e.printStackTrace();
                String fmt = "failed to read session parameter: %s: %s";
                showToast(String.format(fmt, sessionId, e.getMessage()));
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
