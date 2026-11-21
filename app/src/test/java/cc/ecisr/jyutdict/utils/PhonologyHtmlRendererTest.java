package cc.ecisr.jyutdict.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class PhonologyHtmlRendererTest {
    @Test
    public void rendersRecoverablePhonologyState() {
        String html = PhonologyHtmlRenderer.render(report(), false);

        assertTrue(html.contains("var initialState=location.hash||'';"));
        assertTrue(html.contains("JSON.stringify({tab:checked?checked.id:'tab-0'"));
        assertTrue(html.contains("history.replaceState"));
        assertTrue(html.contains("saved.filter"));
        assertTrue(html.contains("saved.left"));
        assertTrue(html.contains("input.dispatchEvent"));
        assertTrue(html.contains("new Event('input',{bubbles:true})"));
        assertTrue(html.contains("Android.onPhonologyScroll"));
        assertTrue(html.contains("history.replaceState"));
        assertTrue(html.contains("if(window.Android&&Android.onPhonologyScroll)"));
        assertTrue(html.contains("Android.onPhonologyScroll(wrap?wrap.scrollLeft:0);"));
    }

    @Test
    public void injectsPreviouslyHorizontalTablePosition() {
        String html = PhonologyHtmlRenderer.withScrollLeft(
                PhonologyHtmlRenderer.render(report(), false), 512);

        assertTrue(html.contains("var initialScrollLeft=512;"));
        assertTrue(html.contains("Number(saved.left)||initialScrollLeft||0"));
        assertTrue(html.contains("Android.onPhonologyScroll(wrap.scrollLeft);"));
        assertTrue(html.contains(
                "wrap.addEventListener('scroll',function(){"
                        + "Android.onPhonologyScroll(wrap.scrollLeft);"));
    }

    @Test
    public void escapesPublicDictionaryText() {
        JSONObject report = report();
        try {
            report.put("locationName", "<script>alert(1)</script>");
            JSONObject section = report.getJSONArray("sections").getJSONObject(0);
            section.put("label", "<img src=x onerror=alert(1)>");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        String html = PhonologyHtmlRenderer.render(report, true);

        assertFalse(html.contains("<script>alert(1)</script>"));
        assertFalse(html.contains("<img src=x onerror=alert(1)>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    private static JSONObject report() {
        try {
            JSONArray conditions = new JSONArray()
                    .put(new JSONArray().put("等").put("一"))
                    .put(new JSONArray().put("攝").put("山"));
            JSONObject outcome = new JSONObject()
                    .put("value", "in")
                    .put("charCount", 1)
                    .put("examples", new JSONArray());
            JSONObject rule = new JSONObject()
                    .put("base", "山")
                    .put("conditions", conditions)
                    .put("outcomes", new JSONArray().put(outcome));
            JSONObject section = new JSONObject()
                    .put("id", "reverse-finals")
                    .put("label", "反查韻母")
                    .put("baseLabel", "中古韻")
                    .put("outcomeLabel", "現代韻母")
                    .put("rules", new JSONArray().put(rule));
            return new JSONObject()
                    .put("locationName", "江門墟頂")
                    .put("sections", new JSONArray().put(section));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
