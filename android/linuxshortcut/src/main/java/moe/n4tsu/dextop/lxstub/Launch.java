package moe.n4tsu.dextop.lxstub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/** Opens the Linux application this launcher was made for, through Dextop. */
public class Launch extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String target = info.metaData.getString("dextop.target");
            Intent intent = new Intent("moe.n4tsu.dextop.action.LAUNCH_LINUX_APP")
                .setComponent(new ComponentName(target, "moe.n4tsu.dextop.linux.LinuxLaunchActivity"))
                .putExtra("container", info.metaData.getString("dextop.container"))
                .putExtra("desktop", info.metaData.getString("dextop.desktop"))
                .putExtra("label", String.valueOf(info.loadLabel(getPackageManager())));
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "Dextop is not installed", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
