package cc.ecisr.jyutdict.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

import cc.ecisr.jyutdict.R;
import cc.ecisr.jyutdict.SettingsActivity;
import cc.ecisr.jyutdict.utils.LocaleUtil;

public final class UpdateNotifier {
    private static final String CHANNEL_ID = "app_updates";
    private static final int NOTIFICATION_ID = 804;
    private static final int PROGRESS_NOTIFICATION_ID = 805;
    private static final int INSTALL_NOTIFICATION_ID = 806;

    private UpdateNotifier() {}

    public static void notifyUpdate(Context context, AppUpdateClient.ReleaseInfo release) {
        Context localizedContext = LocaleUtil.wrap(context.getApplicationContext());
        NotificationManager manager =
                (NotificationManager) localizedContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) {
            android.util.Log.w("UpdateNotifier", "Update notifications are disabled by the system");
            return;
        }
        ensureChannel(localizedContext, manager);
        String releaseKey = releaseKey(release);
        if (UpdateSettings.getLastNotifiedReleaseKey(context).equals(releaseKey)) return;

        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SHOW_UPDATE_DIALOG, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(localizedContext, CHANNEL_ID)
                : new Notification.Builder(localizedContext);
        String notes = UpdateTextFormatter.plainNotes(
                cc.ecisr.jyutdict.utils.ChineseTextConverter.display(release.releaseBody()));
        String contentText = notes.isEmpty()
                ? localizedContext.getString(R.string.update_notification_text)
                : notes;
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(localizedContext.getString(
                        R.string.update_notification_title, release.versionName()))
                .setContentText(contentText)
                .setStyle(new Notification.BigTextStyle().bigText(contentText))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
        UpdateSettings.setLastNotifiedReleaseKey(context, releaseKey);
    }

    /** Shows an ongoing progress notification during a background download. */
    public static void notifyDownloadProgress(Context context,
                                              AppUpdateClient.ReleaseInfo release,
                                              int percent,
                                              long downloadedBytes,
                                              long totalBytes) {
        Context localizedContext = LocaleUtil.wrap(context.getApplicationContext());
        NotificationManager manager =
                (NotificationManager) localizedContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(localizedContext, manager);

        String detail = totalBytes > 0
                ? localizedContext.getString(R.string.update_bg_progress,
                        percent + "%", formatBytes(downloadedBytes), formatBytes(totalBytes))
                : localizedContext.getString(R.string.update_bg_progress_unknown,
                        formatBytes(downloadedBytes));
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(localizedContext, CHANNEL_ID)
                : new Notification.Builder(localizedContext);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(localizedContext.getString(R.string.update_bg_downloading_title))
                .setContentText(detail)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, Math.max(0, percent), totalBytes <= 0)
                .build();
        manager.notify(PROGRESS_NOTIFICATION_ID, notification);
    }

    /** Shows a completed notification that launches the APK installer. */
    public static void notifyInstallReady(Context context,
                                          AppUpdateClient.ReleaseInfo release) {
        Context localizedContext = LocaleUtil.wrap(context.getApplicationContext());
        NotificationManager manager =
                (NotificationManager) localizedContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(localizedContext, manager);

        File updateDir = context.getExternalFilesDir("updates");
        if (updateDir == null) {
            updateDir = new File(context.getFilesDir(), "updates");
        }
        File apkFile = new File(updateDir, release.assetName());
        Uri uri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", apkFile);
        Intent installIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                INSTALL_NOTIFICATION_ID,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(localizedContext, CHANNEL_ID)
                : new Notification.Builder(localizedContext);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(localizedContext.getString(R.string.update_bg_ready_title))
                .setContentText(localizedContext.getString(R.string.update_bg_ready_text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();
        manager.notify(INSTALL_NOTIFICATION_ID, notification);
        // Dismiss the progress notification once done.
        manager.cancel(PROGRESS_NOTIFICATION_ID);
    }

    /** Shows a download failure notification. */
    public static void notifyDownloadFailed(Context context, String message) {
        Context localizedContext = LocaleUtil.wrap(context.getApplicationContext());
        NotificationManager manager =
                (NotificationManager) localizedContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(localizedContext, manager);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(localizedContext, CHANNEL_ID)
                : new Notification.Builder(localizedContext);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(localizedContext.getString(R.string.update_bg_failed_title))
                .setContentText(message == null || message.isEmpty()
                        ? localizedContext.getString(R.string.update_bg_failed_text)
                        : message)
                .setAutoCancel(true)
                .build();
        manager.notify(PROGRESS_NOTIFICATION_ID, notification);
    }

    private static String formatBytes(long value) {
        if (value < 1024) return value + " B";
        double size = value / 1024.0;
        if (size < 1024) return String.format(java.util.Locale.US, "%.1f KB", size);
        size /= 1024.0;
        if (size < 1024) return String.format(java.util.Locale.US, "%.1f MB", size);
        size /= 1024.0;
        return String.format(java.util.Locale.US, "%.2f GB", size);
    }

    private static String releaseKey(AppUpdateClient.ReleaseInfo release) {
        return release.tagName() + "|" + release.buildNumber() + "|" + release.assetName();
    }

    private static void ensureChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.update_channel_description));
        manager.createNotificationChannel(channel);
    }
}
