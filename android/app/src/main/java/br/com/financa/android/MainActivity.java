package br.com.financa.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import com.google.androidbrowserhelper.trusted.LauncherActivity;

/** Entry point keeps an unconfigured debug build usable without a fake server URL. */
public final class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (BuildConfig.CONFIGURED) {
            startActivity(new Intent(this, LauncherActivity.class));
            finish();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding * 2, padding, padding);
        TextView heading = new TextView(this);
        heading.setText(R.string.setup_title);
        heading.setTextSize(26);
        layout.addView(heading);
        TextView message = new TextView(this);
        message.setText(R.string.setup_message);
        message.setTextSize(18);
        message.setPadding(0, padding, 0, 0);
        layout.addView(message);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }
}
