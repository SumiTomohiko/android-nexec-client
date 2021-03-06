package jp.gr.java_conf.neko_daisuki.android.nexec.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.JsonReader;
import android.util.Log;
import android.widget.Toast;

import javax.net.ssl.SSLContext;

import au.com.darkside.XServer.ScreenView;
import au.com.darkside.XServer.XScreenListener;
import au.com.darkside.XServer.XServer;

import jp.gr.java_conf.neko_daisuki.android.nexec.client.share.INexecCallback;
import jp.gr.java_conf.neko_daisuki.android.nexec.client.share.INexecService;
import jp.gr.java_conf.neko_daisuki.android.nexec.client.share.SessionId;
import jp.gr.java_conf.neko_daisuki.fsyscall.Logging;
import jp.gr.java_conf.neko_daisuki.fsyscall.SocketAddress;
import jp.gr.java_conf.neko_daisuki.fsyscall.Unix;
import jp.gr.java_conf.neko_daisuki.fsyscall.UnixDomainAddress;
import jp.gr.java_conf.neko_daisuki.fsyscall.io.AlarmPipe;
import jp.gr.java_conf.neko_daisuki.fsyscall.io.StreamPipe;
import jp.gr.java_conf.neko_daisuki.fsyscall.slave.Alarm;
import jp.gr.java_conf.neko_daisuki.fsyscall.slave.FileMap;
import jp.gr.java_conf.neko_daisuki.fsyscall.slave.Permissions;
import jp.gr.java_conf.neko_daisuki.fsyscall.slave.Slave;
import jp.gr.java_conf.neko_daisuki.fsyscall.slave.SocketCore;
import jp.gr.java_conf.neko_daisuki.fsyscall.util.InvalidPathException;
import jp.gr.java_conf.neko_daisuki.fsyscall.util.PhysicalPath;
import jp.gr.java_conf.neko_daisuki.fsyscall.util.SSLUtil;
import jp.gr.java_conf.neko_daisuki.fsyscall.util.VirtualPath;
import jp.gr.java_conf.neko_daisuki.nexec.client.NexecClient;
import jp.gr.java_conf.neko_daisuki.nexec.client.ProtocolException;

public class MainService extends Service {

    private static class Destination implements Logging.Destination {

        private static final String LOG_TAG = "nexec client service";

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
                public abstract void error(String message) throws RemoteException;
                public abstract void xInvalidate(int left, int top, int right,
                                                 int bottom) throws RemoteException;
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

                @Override
                public void xInvalidate(int left, int top, int right,
                                           int bottom) throws RemoteException {
                }

                @Override
                public void error(String message) throws RemoteException {
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

                @Override
                public void xInvalidate(int left, int top, int right,
                                           int bottom) throws RemoteException {
                    mCallback.xInvalidate(left, top, right, bottom);
                }

                @Override
                public void error(String message) throws RemoteException {
                    mCallback.error(message);
                }
            }

            private class SlaveListener implements Slave.Listener {

                private class Socket implements SocketCore {

                    private InputStream mIn;
                    private OutputStream mOut;
                    private boolean mClosed = false;

                    public Socket(InputStream in, OutputStream out) {
                        mIn = in;
                        mOut = out;
                    }

                    @Override
                    public void close() throws IOException {
                        mIn.close();
                        mOut.close();
                        mClosed = true;
                    }

                    @Override
                    public InputStream getInputStream() {
                        return mIn;
                    }

                    @Override
                    public OutputStream getOutputStream() {
                        return mOut;
                    }

                    @Override
                    public boolean isDisconnected() {
                        return mClosed;
                    }
                }

                @Override
                public SocketCore onConnect(int domain, int type, int protocol,
                                            SocketAddress sockaddr,
                                            Alarm alarm) {
                    if (domain != Unix.Constants.PF_LOCAL) {
                        return null;
                    }
                    UnixDomainAddress addr;
                    try {
                        addr = (UnixDomainAddress)sockaddr;
                    }
                    catch (ClassCastException unused) {
                        return null;
                    }
                    if (!"/tmp/.X11-unix/X0".equals(addr.getPath())) {
                        return null;
                    }
                    XServer xServer = mSession.getXServer();
                    if (xServer == null) {
                        return null;
                    }

                    String fmt = "connecting with X: domain=%d, type=%d, protocol=%d, sockaddr=%s";
                    mLogger.debug(fmt, domain, type, protocol, sockaddr);

                    AlarmPipe serverToClientPipe;
                    StreamPipe clientToServerPipe;
                    try {
                        serverToClientPipe = new AlarmPipe(alarm);
                        clientToServerPipe = new StreamPipe();
                    }
                    catch (IOException e) {
                        handleException("creating pipe", e);
                        return null;
                    }

                    connectToX(xServer, clientToServerPipe, serverToClientPipe);

                    return new Socket(serverToClientPipe.getInputStream(),
                                      clientToServerPipe.getOutputStream());
                }

                private void connectToX(XServer xServer,
                                        StreamPipe clientToServerPipe,
                                        AlarmPipe serverToClientPipe) {
                    InputStream in = clientToServerPipe.getInputStream();
                    OutputStream out = serverToClientPipe.getOutputStream();
                    xServer.connect(in, out);
                }
            }

            private class ScreenListener implements XScreenListener {

                @Override
                public void onPostInvalidate(int left, int top, int right,
                                             int bottom) {
                    try {
                        mCallback.xInvalidate(left, top, right, bottom);
                    }
                    catch (RemoteException e) {
                        handleException("postInvalidate", e);
                    }
                }
            }

            private static final String DIRECTORY_CONTENTS_MARK = "/**";

            private SessionParameter mSessionParameter;
            private Session mSession;
            private NexecClient mNexecClient;

            private Callback mCallback;
            private InputStream mStdin = new Input();
            private OutputStream mStdout = new Stdout();
            private OutputStream mStderr = new Stderr();

            public Task(SessionParameter param, Session session) {
                mSessionParameter = param;
                mSession = session;
            }

            public void setCallback(INexecCallback callback) {
                mCallback = callback != null ? new TrueCallback(callback)
                                             : new NopCallback();
            }

            public void cancelNexec() {
                mNexecClient.cancel();
            }

            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);
                mLogger.info("The task finished.");
            }

            protected Void doInBackground(Void... params) {
                try {
                    run();
                }
                catch (IOException e) {
                    mLogger.err(e.getMessage());
                    e.printStackTrace();
                }
                catch (GeneralSecurityException e) {
                    mLogger.err(e.getMessage());
                    e.printStackTrace();
                }
                catch (InvalidPathException e) {
                    mLogger.err(e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }

            private void run() throws CertificateException,
                                      IOException,
                                      KeyManagementException,
                                      KeyStoreException,
                                      NoSuchAlgorithmException,
                                      InvalidPathException {
                if (mSessionParameter.x) {
                    int xWidth = mSessionParameter.xWidth;
                    int xHeight = mSessionParameter.xHeight;
                    XScreenListener listener = new ScreenListener();
                    XServer xServer = new XServer(MainService.this, xWidth,
                                                  xHeight, listener);
                    mSession.setXServer(xServer);

                    Bitmap.Config config = Bitmap.Config.ARGB_8888;
                    Bitmap bitmap = Bitmap.createBitmap(xWidth, xHeight,
                                                        config);
                    mSession.setBitmap(bitmap);
                }

                Permissions perm = new Permissions();
                for (String path: mSessionParameter.files) {
                    if (path.endsWith(DIRECTORY_CONTENTS_MARK)) {
                        int markLen = DIRECTORY_CONTENTS_MARK.length();
                        int dirLen = path.length() - markLen;
                        String s = path.substring(0, dirLen);
                        PhysicalPath dirPath = new PhysicalPath(s);
                        perm.allowDirectoryContents(dirPath);
                        continue;
                    }
                    perm.allowPath(new PhysicalPath(path));
                }

                mNexecClient = new NexecClient();
                File storage = Environment.getExternalStorageDirectory();
                String path = storage.getAbsolutePath();
                VirtualPath currentDirectory = new VirtualPath(path);
                SlaveListener listener = new SlaveListener();

                InputStream in = getAssets().open("cacerts.bks");
                try {
                    SSLContext context = SSLUtil.createContext("BKS", in,
                                                               "hogehoge");

                    String sessionPath = String.format("%s/%s", getFilesDir(),
                                                       mSession.getSessionId());
                    String resourcePath = String.format("%s/resources",
                                                        sessionPath);
                    if (!new File(resourcePath).mkdirs()) {
                        String fmt = "cannot make directory: %s";
                        handleError(String.format(fmt, resourcePath));
                        return;
                    }
                    try {
                        try {
                            int exitCode = mNexecClient.run(
                                    mSessionParameter.host, mSessionParameter.port,
                                    context, "anonymous", "anonymous",
                                    mSessionParameter.args, currentDirectory,
                                    mStdin, mStdout, mStderr,
                                    mSessionParameter.env, perm,
                                    mSessionParameter.fileMap, listener,
                                    resourcePath);
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
                    finally {
                        deleteDirectory(new File(sessionPath));
                    }
                }
                finally {
                    in.close();
                }
            }

            private void deleteDirectory(File dir) {
                File children[] = dir.listFiles();
                int nChildren = children.length;
                for (int i = 0; i < nChildren; i++) {
                    File child = children[i];
                    if (child.isDirectory()) {
                        deleteDirectory(child);
                        continue;
                    }
                    child.delete();
                }
                dir.delete();
            }

            private void handleError(String message) {
                handleError(message, message);
            }

            private void handleError(String message, String toastMessage) {
                try {
                    mCallback.error(message);
                }
                catch (RemoteException e1) {
                }
                setCallback(null);

                mHandler.post(new ExceptionProcessor(toastMessage));
            }

            private void handleException(String msg, Exception e) {
                String fmt = "nexec service: %s: %s: %s";
                String name = e.getClass().getName();
                String s = String.format(fmt, msg, name, e.getMessage());
                mLogger.err("%s", s);
                e.printStackTrace();

                handleError(msg, s);
            }
        }

        private class Session {

            private SessionId mSessionId;
            private Task mTask;
            private XServer mXServer;
            private Bitmap mBitmap;
            private Canvas mCanvas;

            public Session(SessionId sessionId) {
                mSessionId = sessionId;
            }

            public SessionId getSessionId() {
                return mSessionId;
            }

            public void setTask(Task task) {
                mTask = task;
            }

            public Task getTask() {
                return mTask;
            }

            public XServer getXServer() {
                return mXServer;
            }

            public Bitmap getBitmap() {
                return mBitmap;
            }

            public Canvas getCanvas() {
                return mCanvas;
            }

            public void setBitmap(Bitmap bitmap) {
                mBitmap = bitmap;
                mCanvas = new Canvas(bitmap);
            }

            public void setXServer(XServer xServer) {
                mXServer = xServer;
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
            public NexecClient.Environment env;
            public String[] files = new String[0];
            public FileMap fileMap;
            public boolean x;
            public int xWidth;
            public int xHeight;
        }

        private Sessions mSessions = new Sessions();

        @Override
        public void execute(SessionId sessionId, INexecCallback callback) throws RemoteException {
            mLogger.info("execute: sessionId=%s", sessionId);
            startTask(sessionId, callback);
        }

        @Override
        public void connect(SessionId sessionId, INexecCallback callback)
                throws RemoteException {
            mLogger.info("connect: sessionId=%s", sessionId);
            Session session = getSession(sessionId);
            if (session == null) {
                return;
            }

            session.getTask().setCallback(callback);
        }

        @Override
        public void disconnect(SessionId sessionId) throws RemoteException {
            mLogger.info("disconnect: sessionId=%s", sessionId);
            Session session = getSession(sessionId);
            if (session == null) {
                return;
            }

            session.getTask().setCallback(null);
        }

        @Override
        public void quit(SessionId sessionId) throws RemoteException {
            mLogger.info("quit: sessionId=%s", sessionId);
            Session session = getSession(sessionId);
            if (session == null) {
                return;
            }
            session.getTask().cancelNexec();
            mSessions.remove(sessionId);
        }

        @Override
        public void writeStdin(SessionId sessionId, int b) throws RemoteException {
            // TODO: Write stdin.
        }

        @Override
        public Bitmap xDraw(SessionId sessionId) throws RemoteException {
            mLogger.info("xDraw: sessionId=%s", sessionId);
            Session session = getSession(sessionId);
            if (session == null) {
                return createBlankBitmap();
            }
            Canvas canvas = session.getCanvas();
            if (canvas == null) {
                return createBlankBitmap();
            }
            session.getXServer().getScreen().draw(canvas);
            return session.getBitmap();
        }

        @Override
        public void xLeftButtonPress(SessionId sessionId) throws RemoteException {
            mLogger.info("xLeftButtonPress: sessionId=%s", sessionId);
            Session session = getSession(sessionId);
            if (session == null) {
                return;
            }
            session.getXServer().getScreen().pressLeftButton();
        }

        @Override
        public void xLeftButtonRelease(SessionId sessionId) throws RemoteException {
            mLogger.info("xLeftButtonRelease: sessionId=%s", sessionId);
            Session session = getSession(sessionId);
            if (session == null) {
                return;
            }
            session.getXServer().getScreen().releaseLeftButton();
        }

        @Override
        public void xMotionNotify(SessionId sessionId, int x, int y) throws RemoteException {
            mLogger.info("xMotionNotify: sessionId=%s, x=%d, y=%d",
                         sessionId, x, y);
            Session session = getSession(sessionId);
            if (session == null) {
                return;
            }
            ScreenView view = session.getXServer().getScreen();
            view.updatePointerPosition(x, y, 0);
        }

        @Override
        public void xRightButtonPress(SessionId sessionId) throws RemoteException {
            mLogger.info("xRightButtonPress: sessionId=%s", sessionId);
            Session session = getSession(sessionId);
            if (session == null) {
                return;
            }
            session.getXServer().getScreen().pressRightButton();
        }

        @Override
        public void xRightButtonRelease(SessionId sessionId) throws RemoteException {
            mLogger.info("xRightButtonRelease: sessionId=%s", sessionId);
            Session session = getSession(sessionId);
            if (session == null) {
                return;
            }
            session.getXServer().getScreen().releaseRightButton();
        }

        private Session getSession(SessionId id) {
            Session session = mSessions.get(id);
            if (session == null) {
                mLogger.err("unknown session id given: %s", id);
            }
            return session;
        }

        private Bitmap createBlankBitmap() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        private SessionParameter readSessionParameter(SessionId sessionId) throws IOException, InvalidPathException {
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
                    else if (name.equals("file_map")) {
                        param.fileMap = readFileMap(reader);
                    }
                    else if (name.equals("x")) {
                        param.x = reader.nextBoolean();
                    }
                    else if (name.equals("x_width")) {
                        param.xWidth = reader.nextInt();
                    }
                    else if (name.equals("x_height")) {
                        param.xHeight = reader.nextInt();
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

        private NexecClient.Environment readEnvironment(JsonReader reader) throws IOException {
            NexecClient.Environment env = new NexecClient.Environment();

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

        private FileMap readFileMap(JsonReader reader) throws IOException, InvalidPathException {
            FileMap fileMap = new FileMap();

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

                fileMap.put(new PhysicalPath(dest), new VirtualPath(src));
            }
            reader.endArray();

            return fileMap;
        }

        private void startTask(SessionId sessionId, INexecCallback callback) {
            SessionParameter param;
            try {
                param = readSessionParameter(sessionId);
            }
            catch (IOException e) {
                // The sessionId must be invalid. Ignore it.
                String fmt = "failed to read session parameter (invalid id?): %s: %s";
                mLogger.warn(fmt, sessionId, e.getMessage());
                e.printStackTrace();
                return;
            }
            catch (InvalidPathException e) {
                mLogger.err(e.getMessage());
                e.printStackTrace();
                return;
            }
            if (!removeSessionFile(sessionId)) {
                return;
            }

            Session session = new Session(sessionId);
            Task task = new Task(param, session);
            task.setCallback(callback);
            task.execute();
            session.setTask(task);
            mLogger.info("A new task started for session %s.", sessionId);

            mSessions.put(sessionId, session);
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

    private static Logging.Logger mLogger;

    private Handler mHandler = new Handler();

    public void onCreate() {
        super.onCreate();
        mLogger.info("MainService was created.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Stub();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    static {
        Logging.setDestination(new Destination());
        mLogger = new Logging.Logger("MainService");
    }
}

// vim: tabstop=4 shiftwidth=4 expandtab softtabstop=4
