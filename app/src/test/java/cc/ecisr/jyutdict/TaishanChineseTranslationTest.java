package cc.ecisr.jyutdict;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class TaishanChineseTranslationTest {
    @Test public void prefersChineseMeaningAndNeverUsesEnglishAsChinese() {
        assertEquals("台山话", TaishanChineseTranslation.chineseMeaning("台山话", "台山話"));
        assertEquals("台山話", TaishanChineseTranslation.chineseMeaning("-", "台山話"));
        assertEquals("", TaishanChineseTranslation.chineseMeaning("Taishan", ""));
        assertEquals("", TaishanChineseTranslation.chineseMeaning(null, null));
    }

    @Test public void preservesBothSourceFieldsWithoutRelabelingThem() {
        assertEquals("Taishan\nName of a county", TaishanChineseTranslation.sourceText(
                "Taishan", "Name of a county"));
        assertTrue(TaishanChineseTranslation.needsTranslation("Name of a county"));
        assertFalse(TaishanChineseTranslation.needsTranslation("县名"));
        assertEquals("县名", TaishanChineseTranslation.sourceText("", "县名"));
    }

    @Test public void respectsProviderUtf8LimitAndKeepsSupplementaryCharacters() throws Exception {
        String source = "county 台山 \uD840\uDC00 ".repeat(100);
        for (String chunk : TaishanChineseTranslation.chunks(source)) {
            assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= 450);
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
            assertFalse(Character.isLowSurrogate(chunk.charAt(0)));
        }
        assertTrue(TaishanChineseTranslation.chunks("").isEmpty());
    }

    @Test public void rejectsQuotaFailuresAndNonChineseResponses() throws Exception {
        assertEquals("县名", TaishanChineseTranslation.parseResponse(
                "{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"县名\"}}"));
        for (String json : new String[]{
                "{\"responseStatus\":429}",
                "{\"responseStatus\":200,\"quotaFinished\":true}",
                "{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"MYMEMORY WARNING\"}}",
                "{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"\"}}"}) {
            try {
                TaishanChineseTranslation.parseResponse(json);
                fail("Must not display provider errors as definitions");
            } catch (java.io.IOException expected) { }
        }
    }

    @Test public void showsChineseByDefaultAndExpandsOriginalWithoutNetwork() {
        checkChineseRow(R.style.AppTheme);
        checkChineseRow(R.style.DarkTheme);
    }

    private void checkChineseRow(int theme) {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), theme);
        TaishanOnlineAdapter adapter = new TaishanOnlineAdapter();
        try {
            adapter.submit(Collections.singletonList(new TaishanAudioSource.DictionaryEntry(
                    "台山", "hoi22 san33", "台山", "台山", "Taishan", "Name of a county", null)));
            TaishanOnlineAdapter.Holder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
            adapter.onBindViewHolder(holder, 0);
            assertEquals("台山", holder.english.getText().toString());
            assertEquals(View.GONE, holder.original.getVisibility());
            assertEquals(View.GONE, holder.definition.getVisibility());
            assertEquals(View.VISIBLE, holder.translate.getVisibility());
            int width = (int) (280 * context.getResources().getDisplayMetrics().density);
            holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            holder.itemView.layout(0, 0, width, holder.itemView.getMeasuredHeight());
            assertTrue(holder.translate.getRight() <= ((View) holder.translate.getParent()).getWidth());
            assertTrue(holder.originalToggle.getRight() <= ((View) holder.originalToggle.getParent()).getWidth());
            holder.originalToggle.performClick();
            adapter.onBindViewHolder(holder, 0);
            assertEquals(View.VISIBLE, holder.original.getVisibility());
            assertEquals("Taishan\nName of a county", holder.original.getText().toString());
            adapter.submit(Collections.singletonList(new TaishanAudioSource.DictionaryEntry(
                    "话", "va22", "話", "话", "", "中文说明", null)));
            adapter.onBindViewHolder(holder, 0);
            assertEquals(View.GONE, holder.original.getVisibility());
            assertEquals(View.GONE, holder.translate.getVisibility());
            assertEquals("中文说明", holder.definition.getText().toString());
        } finally {
            adapter.dispose();
        }
    }
}
