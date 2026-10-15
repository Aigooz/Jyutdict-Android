package cc.ecisr.jyutdict.update;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

public final class AppUpdateWorker extends Worker {
    private static final String TAG = "AppUpdateWorker";

    public AppUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            AppUpdateClient.UpdateResult result =
                    AppUpdateClient.fetchLatestReleaseSync(getApplicationContext());
            if (result.hasUpdate() && result.release() != null) {
                Context context = getApplicationContext();
                AppUpdateClient.ReleaseInfo release = result.release();
                if (UpdateDownloader.isDownloaded(context, release)) {
                    UpdateNotifier.notifyInstallReady(context, release);
                } else if (!isBackgroundDownloadActive()) {
                    UpdateNotifier.notifyUpdate(context, release);
                }
            }
            return Result.success();
        } catch (Exception exception) {
            Log.w(TAG, "Update check failed", exception);
            return Result.retry();
        }
    }

    private boolean isBackgroundDownloadActive() throws Exception {
        for (WorkInfo info : WorkManager.getInstance(getApplicationContext())
                .getWorkInfosForUniqueWork(
                        BackgroundUpdateWorker.UNIQUE_WORK_NAME).get()) {
            if (info.getState() == WorkInfo.State.ENQUEUED
                    || info.getState() == WorkInfo.State.RUNNING) {
                return true;
            }
        }
        return false;
    }
}
