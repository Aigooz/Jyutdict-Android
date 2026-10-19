package cc.ecisr.jyutdict.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class DiskTextCacheTest {
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DiskTextCache.clear(context);
    }

    @Test
    public void writesAreReadableAndRemovable() {
        assertNull(DiskTextCache.readAny(context, "entry-1"));
        DiskTextCache.write(context, "entry-1", "字表");
        assertEquals("字表", DiskTextCache.readAny(context, "entry-1").trim());
        DiskTextCache.remove(context, "entry-1");
        assertNull(DiskTextCache.readAny(context, "entry-1"));
    }

    @Test
    public void freshReadRespectsAge() {
        DiskTextCache.write(context, "article", "內文");
        assertNotNull(DiskTextCache.readFresh(context, "article", 60_000L));
        touchAllEntriesTo(context, System.currentTimeMillis() - 2 * DAY_MILLIS);
        assertNull(DiskTextCache.readFresh(context, "article", DAY_MILLIS));
    }

    @Test
    public void laterWritesPruneEntriesOlderThanTheRetentionWindow() {
        DiskTextCache.write(context, "stale", "舊結果");
        touchAllEntriesTo(context, System.currentTimeMillis() - 31 * DAY_MILLIS);
        DiskTextCache.write(context, "fresh", "新結果");
        assertNull(DiskTextCache.readAny(context, "stale"));
        assertNotNull(DiskTextCache.readAny(context, "fresh"));
    }

    private static void touchAllEntriesTo(Context context, long timestamp) {
        File[] files = new File(context.getCacheDir(), "readonly_http").listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile()) file.setLastModified(timestamp);
        }
    }
}
