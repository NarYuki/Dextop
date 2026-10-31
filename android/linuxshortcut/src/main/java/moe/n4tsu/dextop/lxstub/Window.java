package moe.n4tsu.dextop.lxstub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/**
 * The root of one window's task. Dextop's window is opened on top of it, inside this task,
 * so the task belongs to this launcher: Android and DeX name it and draw its icon after the
 * Linux application. Once Dextop's window closes, the task ends.
 */
public class Window extends Activity {
    private static final int HOST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A recreated task still has Dextop's window above this one.
        if (savedInstanceState != null) return;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Intent intent = new Intent()
                .setComponent(new ComponentName(info.metaData.getString("dextop.target"),
                    "moe.n4tsu.dextop.linux.LinuxHostedWindowActivity"))
                .putExtra("xid", getIntent().getIntExtra("xid", 0))
                .putExtra("label", String.valueOf(info.loadLabel(getPackageManager())))
                .putExtra("icon", getIntent().getByteArrayExtra("icon"));
            // Only this launcher's own application, whoever opened this activity.
            intent.putExtra("container", info.metaData.getString("dextop.container"))
                .putExtra("desktop", info.metaData.getString("dextop.desktop"));
            startActivityForResult(intent, HOST);
        } catch (Exception error) {
            Toast.makeText(this, R.string.lxstub_dextop_missing, Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        finishAndRemoveTask();
    }
}
