package cc.ecisr.jyutdict;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import cc.ecisr.jyutdict.databinding.ActivityAboutBinding;
import cc.ecisr.jyutdict.utils.ImmersiveBarUtil;
import cc.ecisr.jyutdict.utils.ThemeUtil;
import cc.ecisr.jyutdict.utils.ToastUtil;

/** Central page for maintainer, project provenance and content source links. */
public class AboutActivity extends LocalizedActivity {
    private static final String PROFILE_URL = "https://github.com/Aigooz";
    private static final String UPDATE_RELEASE_URL =
            "https://github.com/Aigooz/Jyutdict-Android/releases/latest";
    private static final String ORIGINAL_PROJECT_URL =
            "https://github.com/JyutdictEB/Jyutdict-Android";
    private static final String CONTENT_SITE_URL = "https://www.jyutdict.org";

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeUtil.isNightMode(this) ? R.style.DarkTheme : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ImmersiveBarUtil.setImmersiveBar(this, false, !ThemeUtil.isNightMode(this));
        ImmersiveBarUtil.applyToolbarInsets(binding.toolbar);
        ImmersiveBarUtil.applyBottomInsets(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.aboutVersionValue.setText(getString(
                R.string.about_version_value, getInstalledVersionName()));
        binding.btnOpenProfile.setOnClickListener(v -> openUrl(PROFILE_URL));
        binding.btnOpenSource.setOnClickListener(v ->
                openUrl("https://github.com/Aigooz/Jyutdict-Android"));
        binding.btnOpenUpstream.setOnClickListener(v -> openUrl(ORIGINAL_PROJECT_URL));
        binding.btnOpenLicense.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.about_license_title)
                .setMessage(readLicense())
                .setPositiveButton(R.string.location_close, null)
                .show());
        binding.btnOpenUpdateRelease.setOnClickListener(v -> openUrl(UPDATE_RELEASE_URL));
        binding.btnOpenOriginalProject.setOnClickListener(v -> openUrl(ORIGINAL_PROJECT_URL));
        binding.btnOpenContentSite.setOnClickListener(v -> openUrl(CONTENT_SITE_URL));
        binding.btnOpenHelp.setOnClickListener(v -> startActivity(
                new Intent(this, InfoActivity.class)));
    }

    private String readLicense() {
        try (java.io.InputStream input = getAssets().open("licenses/MIT.txt");
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toString("UTF-8");
        } catch (java.io.IOException exception) {
            return getString(R.string.about_original_summary);
        }
    }

    private String getInstalledVersionName() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
            return versionName == null ? "0.0.0" : versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "0.0.0";
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception exception) {
            ToastUtil.msg(this, getString(R.string.version_link_unavailable));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
