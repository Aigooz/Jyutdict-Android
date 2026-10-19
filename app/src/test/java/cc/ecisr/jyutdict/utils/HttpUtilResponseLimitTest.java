package cc.ecisr.jyutdict.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class HttpUtilResponseLimitTest {
    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readsBodiesBelowTheLimit() throws Exception {
        assertEquals("ab\n", HttpUtil.readBody(stream("ab"), 16));
    }

    @Test
    public void rejectsBodiesAboveTheLimit() {
        IOException failure = assertThrows(IOException.class,
                () -> HttpUtil.readBody(stream("aaaaaaaaaaaaaaaaaaaaaaaaaa"), 8));
        assertEquals(HttpUtil.ResponseTooLargeException.class, failure.getClass());
    }
}
