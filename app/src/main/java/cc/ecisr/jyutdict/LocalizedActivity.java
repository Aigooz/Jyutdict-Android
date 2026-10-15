package cc.ecisr.jyutdict;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import cc.ecisr.jyutdict.utils.LocaleUtil;

/** Base activity that supplies the app-wide language override. */
public abstract class LocalizedActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleUtil.wrap(newBase));
    }
}
