package cc.ecisr.jyutdict.update;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import cc.ecisr.jyutdict.R;
import cc.ecisr.jyutdict.databinding.DialogUpdatePromptBinding;
import cc.ecisr.jyutdict.databinding.DialogVersionProgressBinding;
import cc.ecisr.jyutdict.utils.ChineseTextConverter;
import cc.ecisr.jyutdict.utils.ToastUtil;

/**
 * Shows the foreground update prompt after an app-start check, while leaving the
 * WorkManager notification path for checks that happen while the app is closed.
 */
public final class UpdatePromptController {
    private static final long STARTUP_PROMPT_DELAY_MS = 900L;
    private static boolean promptCheckedThisProcess;

    private final AppCompatActivity activity;
    private AlertDialog promptDialog;
    private AlertDialog progressDialog;
    private DialogVersionProgressBinding progressBinding;
    private UpdateDownloader.DownloadHandle downloadHandle;
    private AppUpdateClient.ReleaseInfo selectedRelease;

    private UpdatePromptController(AppCompatActivity activity) {
        this.activity = activity;
    }

    public static void checkAndPrompt(AppCompatActivity activity) {
        if (promptCheckedThisProcess) return;
        promptCheckedThisProcess = true;
        new UpdatePromptController(activity).check(STARTUP_PROMPT_DELAY_MS);
    }

    private void check(long delayMs) {
        activity.getWindow().getDecorView().postDelayed(() -> {
            AppUpdateClient.fetchLatestRelease(activity, result -> {
                if (!activityAlive() || !result.hasUpdate() || result.release() == null) return;
                showPrompt(result.release());
            });
        }, delayMs);
    }

    private void showPrompt(AppUpdateClient.ReleaseInfo release) {
        if (!activityAlive() || release == null || !release.hasAsset()
                || release.buildNumber() <= UpdateSettings.getDismissedBuild(activity)) {
            return;
        }

        selectedRelease = release;
        DialogUpdatePromptBinding binding = DialogUpdatePromptBinding.inflate(
                activity.getLayoutInflater());
        binding.updateVersion.setText(UpdateTextFormatter.versionLabel(release.versionName()));
        binding.updateMeta.setText(release.assetSize() > 0
                ? activity.getString(R.string.prompt_update_meta,
                        release.buildNumber(),
                        UpdateTextFormatter.formatBytes(release.assetSize()))
                : activity.getString(R.string.prompt_update_build, release.buildNumber()));

        String notes = ChineseTextConverter.display(release.releaseBody());
        if (notes == null || notes.trim().isEmpty()) {
            notes = activity.getString(R.string.prompt_update_no_notes);
        }
        binding.updateNotesTitle.setText(R.string.prompt_update_notes_title);
        binding.updateNotes.setText(UpdateTextFormatter.styledNotes(notes, activity));
        binding.updateNotes.setMovementMethod(new ScrollingMovementMethod());
        UpdateSettings.setLastNotifiedReleaseKey(activity,
                release.tagName() + "|" + release.buildNumber() + "|" + release.assetName());

        boolean downloaded = UpdateDownloader.isDownloaded(activity, release);
        int positiveText = downloaded
                ? R.string.prompt_update_install_ready
                : R.string.prompt_update_install;
        binding.updateInstall.setText(positiveText);
        binding.updateInstall.setOnClickListener(view -> startForegroundDownload());
        binding.updateBackground.setOnClickListener(view -> {
            startBackgroundDownload();
            if (promptDialog != null) promptDialog.dismiss();
        });
        binding.updateLater.setOnClickListener(view -> {
            UpdateSettings.setDismissedBuild(activity, release.buildNumber());
            if (promptDialog != null) promptDialog.dismiss();
        });

        promptDialog = new AlertDialog.Builder(activity)
                .setView(binding.getRoot())
                .setOnDismissListener(dialog -> {
                    promptDialog = null;
                    if (downloadHandle == null) {
                        UpdateSettings.setDismissedBuild(activity, release.buildNumber());
                    }
                })
                .create();
        promptDialog.show();
        if (promptDialog.getWindow() != null) {
            promptDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
        }
    }

    private void startForegroundDownload() {
        if (!activityAlive() || selectedRelease == null || downloadHandle != null) return;
        AppUpdateClient.ReleaseInfo release = selectedRelease;
        downloadHandle = UpdateDownloader.downloadAndInstall(activity, release,
                new UpdateDownloader.ProgressCallback() {
                    @Override
                    public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                        if (!activityAlive() || progressBinding == null) return;
                        if (totalBytes > 0) {
                            progressBinding.versionProgressBar.setIndeterminate(false);
                            progressBinding.versionProgressBar.setMax(100);
                            progressBinding.versionProgressBar.setProgress(Math.max(0, percent));
                            progressBinding.versionProgressPercent.setText(percent + "%");
                            progressBinding.versionProgressDetail.setText(activity.getString(
                                    R.string.version_download_size_progress,
                                    UpdateTextFormatter.formatBytes(downloadedBytes),
                                    UpdateTextFormatter.formatBytes(totalBytes)));
                        } else {
                            progressBinding.versionProgressBar.setIndeterminate(true);
                            progressBinding.versionProgressPercent.setText("--");
                            progressBinding.versionProgressDetail.setText(activity.getString(
                                    R.string.version_download_bytes,
                                    UpdateTextFormatter.formatBytes(downloadedBytes)));
                        }
                    }

                    @Override
                    public void onComplete(boolean success, String message) {
                        downloadHandle = null;
                        if (!activityAlive()) return;
                        hideProgress();
                        if (success) {
                            ToastUtil.msg(activity, activity.getString(
                                    R.string.version_download_complete));
                        } else if (message != null && !message.equals(
                                activity.getString(R.string.version_download_cancelled))) {
                            ToastUtil.msg(activity, activity.getString(
                                    R.string.version_download_failed,
                                    message == null ? "" : message));
                            promptCheckedThisProcess = false;
                        }
                    }
                });
        if (promptDialog != null) promptDialog.dismiss();
        showProgress();
    }

    private void startBackgroundDownload() {
        if (!activityAlive() || selectedRelease == null) return;
        if (UpdateDownloader.isDownloaded(activity, selectedRelease)) {
            startForegroundDownload();
            return;
        }
        UpdateSettings.setDismissedBuild(activity, selectedRelease.buildNumber());
        BackgroundUpdateWorker.enqueue(activity, selectedRelease);
        ToastUtil.msg(activity, activity.getString(R.string.version_background_started));
    }

    private void showProgress() {
        if (!activityAlive() || progressDialog != null) return;
        progressBinding = DialogVersionProgressBinding.inflate(activity.getLayoutInflater());
        progressBinding.versionProgressCancel.setOnClickListener(view -> {
            if (downloadHandle != null) downloadHandle.cancel();
            if (progressDialog != null) progressDialog.dismiss();
        });
        progressDialog = new AlertDialog.Builder(activity)
                .setView(progressBinding.getRoot())
                .setCancelable(true)
                .create();
        progressDialog.setOnDismissListener(dialog -> {
            if (downloadHandle != null) downloadHandle.cancel();
        });
        progressDialog.show();
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
        }
    }

    private void hideProgress() {
        if (progressDialog != null) {
            progressDialog.setOnDismissListener(null);
            progressDialog.dismiss();
        }
        progressDialog = null;
        progressBinding = null;
    }

    private boolean activityAlive() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }
}
