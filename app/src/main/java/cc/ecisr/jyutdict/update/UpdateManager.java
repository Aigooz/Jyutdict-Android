package cc.ecisr.jyutdict.update;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class UpdateManager {
    private static final String UNIQUE_WORK_NAME = "jyutdict_private_update_check";

    private UpdateManager() {}

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
