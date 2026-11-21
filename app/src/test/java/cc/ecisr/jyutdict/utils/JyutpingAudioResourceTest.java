package cc.ecisr.jyutdict.utils;

import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class JyutpingAudioResourceTest {
    @Test
    public void packagedCatalogRecognizesJyut6() {
        Context context = ApplicationProvider.getApplicationContext();
        JyutpingAudioCatalog catalog = JyutpingAudioCatalog.read(context);

        assertFalse(catalog.resolveSequence("jyu6").isEmpty());
    }
}
