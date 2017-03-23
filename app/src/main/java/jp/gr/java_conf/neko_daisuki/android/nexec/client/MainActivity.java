package jp.gr.java_conf.neko_daisuki.android.nexec.client;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.JsonWriter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private class PrivatePagerAdapter extends PagerAdapter {

        private abstract class PageCreator {

            public View create(ViewPager pager, int position) {
                View view = mInflater.inflate(getResourceId(), pager, false);
                initializeView(view);
                pager.addView(view);
                return view;
            }

            protected abstract int getResourceId();
            protected abstract void initializeView(View view);

            protected TextView getTextView(View view, int id) {
                return (TextView)view.findViewById(id);
            }

            @SuppressWarnings("unchecked")
            protected AdapterView<Adapter> getAdapterView(View view, int id) {
                return (AdapterView<Adapter>)view.findViewById(id);
            }
        }

        private class HostPageCreator extends PageCreator {

            protected int getResourceId() {
                return R.layout.page_host;
            }

            protected void initializeView(View view) {
                setStringText(view, R.id.host, mHost);
                setIntText(view, R.id.port, mPort);
            }

            private void setIntText(View view, int id, int n) {
                getTextView(view, id).setText(Integer.toString(n));
            }

            private void setStringText(View view, int id, String s) {
                getTextView(view, id).setText(s);
            }
        }

        private class CommandPageCreator extends PageCreator {

            protected int getResourceId() {
                return R.layout.page_command;
            }

            protected void initializeView(View view) {
                setStringArrayText(view, R.id.args, mArgs);
            }

            private void setStringArrayText(View view, int id, String[] sa) {
                StringBuffer buffer = new StringBuffer(sa[0]);
                for (int i = 1; i < sa.length; i++) {
                    buffer.append(String.format(" %s", sa[i]));
                }
                getTextView(view, id).setText(buffer.toString());
            }
        }

        private class EnvPageCreator extends PageCreator {

            protected int getResourceId() {
                return R.layout.page_environment;
            }

            protected void initializeView(View view) {
                AdapterView<Adapter> listView = getAdapterView(view,
                                                               R.id.env_list);

                int id = android.R.layout.simple_list_item_1;

                List<String> list = new ArrayList<String>();
                for (Environment env: mEnv) {
                    list.add(String.format("%s=%s", env.name, env.value));
                }

                Adapter adapter = new ArrayAdapter<String>(MainActivity.this,
                                                           id,
                                                           list);
                listView.setAdapter(adapter);
            }
        }

        private class FileMappingPageCreator extends PageCreator {

            protected int getResourceId() {
                return R.layout.page_file_mapping;
            }

            protected void initializeView(View view) {
                AdapterView<Adapter> listView = getAdapterView(view,
                                                               R.id.link_list);

                int id = android.R.layout.simple_list_item_1;

                List<String> list = new ArrayList<String>();
                for (FileMap entry: mFileMap) {
                    list.add(String.format("%s -> %s", entry.src, entry.dest));
                }

                Adapter adapter = new ArrayAdapter<String>(MainActivity.this,
                                                           id,
                                                           list);
                listView.setAdapter(adapter);
            }
        }

        private class PermissionPageCreator extends PageCreator {

            protected int getResourceId() {
                return R.layout.page_permission;
            }

            protected void initializeView(View view) {
                setPermissionList(view, mFiles);
            }

            private void setPermissionList(View view, String[] files) {
                int listId = R.id.permission_list;
                AdapterView<Adapter> listView = getAdapterView(view, listId);

                int id = android.R.layout.simple_list_item_1;

                List<String> list = new ArrayList<String>();
                for (String file: files) {
                    list.add(file);
                }

                Adapter adapter = new ArrayAdapter<String>(MainActivity.this,
                                                           id,
                                                           list);
                listView.setAdapter(adapter);
            }
        }

        private class Page {

            private PageCreator mCreator;
            private String mTitle;

            public Page(PageCreator creator, String title) {
                mCreator = creator;
                mTitle = title;
            }

            public PageCreator getCreator() {
                return mCreator;
            }

            public String getTitle() {
                return mTitle;
            }
        }

        private String mHost;
        private int mPort;
        private String[] mArgs;
        private Environment[] mEnv;
        private String[] mFiles;
        private FileMap[] mFileMap;

        private LayoutInflater mInflater;
        private Page[] mPages;

        public PrivatePagerAdapter(String host, int port, String[] args, Environment[] env, String[] files, FileMap[] fileMap) {
            mHost = host;
            mPort = port;
            mArgs = args;
            mEnv = env;
            mFiles = files;
            mFileMap = fileMap;

            String key = Context.LAYOUT_INFLATER_SERVICE;
            mInflater = (LayoutInflater)getSystemService(key);
            mPages = new Page[] {
                new Page(new HostPageCreator(), "Host"),
                new Page(new CommandPageCreator(), "Command"),
                new Page(new EnvPageCreator(), "Environment"),
                new Page(new PermissionPageCreator(), "Permission"),
                new Page(new FileMappingPageCreator(), "File mapping") };
        }

        @Override
        public void destroyItem(View collection, int position, Object view) {
            ViewPager pager = (ViewPager)collection;
            View v = (View)view;
            pager.removeView(v);
        }

        @Override
        public void finishUpdate(View collection) {
        }

        @Override
        public int getCount() {
            return mPages.length;
        }

        @Override
        public Object instantiateItem(View collection, int position) {
            ViewPager pager = (ViewPager)collection;
            return mPages[position].getCreator().create(pager, position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mPages[position].getTitle();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == (View)object;
        }

        @Override
        public void restoreState(Parcelable parcel, ClassLoader classLoader) {
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void startUpdate(View collection) {
        }
    }

    private class OkButtonOnClickListener implements OnClickListener {

        private String mHost;
        private int mPort;
        private String[] mArgs;
        private Environment[] mEnv;
        private String[] mFiles;
        private FileMap[] mFileMap;
        private boolean mX;
        private int mXWidth;
        private int mXHeight;

        public OkButtonOnClickListener(String host, int port, String[] args,
                                       Environment[] env, String[] files,
                                       FileMap[] fileMap, boolean x, int xWidth,
                                       int xHeight) {
            mHost = host;
            mPort = port;
            mArgs = args;
            mEnv = env;
            mFiles = files;
            mFileMap = fileMap;
            mX = x;
            mXWidth = xWidth;
            mXHeight = xHeight;
        }

        public void onClick(View view) {
            String sessionId;
            try {
                sessionId = createSessionId();
            }
            catch (NoSuchAlgorithmException e) {
                showException("algorithm not found", e);
                return;
            }
            try {
                saveSession(sessionId);
            }
            catch (IOException e) {
                showException("failed to save session", e);
                return;
            }

            Intent intent = getIntent();
            intent.putExtra("SESSION_ID", sessionId);
            setResult(RESULT_OK, intent);
            finish();
        }

        private void showException(String message, Throwable e) {
            showToast(String.format("%s: %s", message, e.getMessage()));
            e.printStackTrace();
        }

        private String stringOfDigest(byte[] input) {
            StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < input.length; i++) {
                buffer.append(String.format("%02x", input[i]));
            }
            return buffer.toString();
        }

        private String createSessionId() throws NoSuchAlgorithmException {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(mHost.getBytes());
            for (int width: new int[] { 0, 8, 16, 24 }) {
                md.update((byte)((mPort >>> width) & 0xff));
            }
            for (String a: mArgs) {
                md.update(a.getBytes());
            }
            long now = System.currentTimeMillis();
            for (int width: new int[] { 0, 8, 16, 24, 32, 40, 48, 56 }) {
                md.update((byte)((now >>> width) & 0xff));
            }
            byte[] bytes = new byte[32];
            mRandom.nextBytes(bytes);
            md.update(bytes);
            return stringOfDigest(md.digest());
        }

        private void writeEnvironmentArray(JsonWriter writer, String name, Environment[] env) throws IOException {
            writer.name(name);
            writer.beginArray();
            for (Environment e: env) {
                writer.beginObject();
                writer.name("name").value(e.name);
                writer.name("value").value(e.value);
                writer.endObject();
            }
            writer.endArray();
        }

        private void writeLinkArray(JsonWriter writer, String name, FileMap[] fileMap) throws IOException {
            writer.name(name);
            writer.beginArray();
            for (FileMap entry: fileMap) {
                writer.beginObject();
                writer.name("dest").value(entry.dest);
                writer.name("src").value(entry.src);
                writer.endObject();
            }
            writer.endArray();
        }

        private void writeStringArray(JsonWriter writer, String name, String[] sa) throws IOException {
            writer.name(name);
            writer.beginArray();
            for (String a: sa) {
                writer.value(a);
            }
            writer.endArray();
        }

        private void saveSession(String sessionId) throws IOException {
            OutputStream out = openFileOutput(sessionId, 0);
            JsonWriter writer = new JsonWriter(
                    new BufferedWriter(new OutputStreamWriter(out, "UTF-8")));
            try {
                writer.beginObject();
                writer.name("host").value(mHost);
                writer.name("port").value(mPort);
                writeStringArray(writer, "args", mArgs);
                writeEnvironmentArray(writer, "env", mEnv);
                writeStringArray(writer, "files", mFiles);
                writeLinkArray(writer, "file_map", mFileMap);
                writer.name("x").value(mX);
                writer.name("x_width").value(mXWidth);
                writer.name("x_height").value(mXHeight);
                writer.endObject();
            }
            finally {
                writer.close();
            }
        }
    }

    private class CancelButtonOnClickListener implements OnClickListener {

        public void onClick(View view) {
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    private static class Environment {

        public String name;
        public String value;

        public Environment(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    private static class FileMap {

        public String dest;
        public String src;

        public FileMap(String dest, String src) {
            this.dest = dest;
            this.src = src;
        }
    }

    private Random mRandom = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        String host = intent.getStringExtra("HOST");
        int port = intent.getIntExtra("PORT", 57005);
        String[] args = intent.getStringArrayExtra("ARGS");
        Environment[] env = getEnvironments(intent);
        String[] files = intent.getStringArrayExtra("FILES");
        FileMap[] fileMap = parseFileMap(intent.getStringArrayExtra("FILE_MAP"));
        boolean x = intent.getBooleanExtra("X", false);
        int xWidth = intent.getIntExtra("X_WIDTH", 0);
        int xHeight = intent.getIntExtra("X_HEIGHT", 0);

        ViewPager pager = (ViewPager)findViewById(R.id.view_pager);
        pager.setAdapter(
                new PrivatePagerAdapter(host, port, args, env, files, fileMap));

        Button okButton = (Button)findViewById(R.id.ok_button);
        OnClickListener okButtonListener = new OkButtonOnClickListener(host,
                                                                       port,
                                                                       args,
                                                                       env,
                                                                       files,
                                                                       fileMap,
                                                                       x,
                                                                       xWidth,
                                                                       xHeight);
        okButton.setOnClickListener(okButtonListener);
        Button cancelButton = (Button)findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new CancelButtonOnClickListener());
    }

    private String[] splitFields(String s) {
        StringBuilder buffer = new StringBuilder();
        int i;
        for (i = 0; s.charAt(i) != ':'; i++) {
            i += s.charAt(i) == '\\' ? 1 : 0;
            buffer.append(s.charAt(i));
        }

        StringBuilder buffer2 = new StringBuilder();
        int len = s.length();
        for (i = i + 1; i < len; i++) {
            i += s.charAt(i) == '\\' ? 1 : 0;
            buffer2.append(s.charAt(i));
        }

        return new String[] { buffer.toString(), buffer2.toString() };
    }

    private FileMap parseFileMapEntry(String s) {
        String[] fields = splitFields(s);
        return new FileMap(fields[0], fields[1]);
    }

    private FileMap[] parseFileMap(String[] links) {
        List<FileMap> l = new LinkedList<FileMap>();
        for (String link: links) {
            l.add(parseFileMapEntry(link));
        }
        return l.toArray(new FileMap[0]);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private Environment parseEnvironment(String s) {
        String[] fields = splitFields(s);
        return new Environment(fields[0], fields[1]);
    }

    private Environment[] parseEnvironments(String[] sa) {
        List<Environment> l = new LinkedList<Environment>();
        for (String s: sa) {
            l.add(parseEnvironment(s));
        }
        return l.toArray(new Environment[0]);
    }

    private Environment[] getEnvironments(Intent intent) {
        return parseEnvironments(intent.getStringArrayExtra("ENV"));
    }
}

// vim: tabstop=4 shiftwidth=4 expandtab softtabstop=4
