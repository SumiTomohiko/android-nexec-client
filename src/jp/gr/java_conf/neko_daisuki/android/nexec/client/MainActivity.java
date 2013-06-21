package jp.gr.java_conf.neko_daisuki.android.nexec.client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private class OkButtonOnClickListener implements OnClickListener {

        public void onClick(View view) {
            Intent intent = getIntent();
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    private class CancelButtonOnClickListener implements OnClickListener {

        public void onClick(View view) {
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        setStringExtraText(R.id.host, intent, "HOST");
        setIntExtraText(R.id.port, intent, "PORT");
        setStringArrayExtraText(R.id.args, intent, "ARGS");

        Button okButton = (Button)findViewById(R.id.ok_button);
        okButton.setOnClickListener(new OkButtonOnClickListener());
        Button cancelButton = (Button)findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new CancelButtonOnClickListener());
    }

    private void setIntExtraText(int id, Intent intent, String key) {
        TextView view = (TextView)findViewById(id);
        view.setText(Integer.toString(intent.getIntExtra(key, 57005)));
    }

    private void setStringArrayExtraText(int id, Intent intent, String key) {
        TextView view = (TextView)findViewById(id);
        String[] args = intent.getStringArrayExtra(key);
        StringBuffer buffer = new StringBuffer(args[0]);
        for (int i = 1; i < args.length; i++) {
            buffer.append(String.format(" %s", args[i]));
        }
        view.setText(buffer.toString());
    }

    private void setStringExtraText(int id, Intent intent, String key) {
        TextView view = (TextView)findViewById(id);
        view.setText(intent.getStringExtra(key));
    }
}

// vim: tabstop=4 shiftwidth=4 expandtab softtabstop=4
