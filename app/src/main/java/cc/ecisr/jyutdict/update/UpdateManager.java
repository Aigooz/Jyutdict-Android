package cc.ecisr.jyutdict.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String UNIQUE_WORK_NAME = "jyutdict_private_update_check";

    private UpdateManager() {}

    /** Android 8 and later only open a sideloaded APK after the user grants that right. */
    public static boolean canInstallApk(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return context.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent installPermissionIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.fromParts("package", context.getPackageName(), null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static void openInstallPermissionPage(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            context.startActivity(installPermissionIntent(context));
        } catch (RuntimeException exception) {
            Log.w(TAG, "Install permission settings are unavailable", exception);
        }
    }

    public static void schedulePeriodicCheck(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AppUpdateWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void checkNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AppUpdateWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME + "_now", ExistingWorkPolicy.REPLACE, request);
    }
}
