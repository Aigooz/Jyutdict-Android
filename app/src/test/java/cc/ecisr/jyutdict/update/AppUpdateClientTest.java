package cc.ecisr.jyutdict.update;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppUpdateClientTest {
    @Test
    public void parsesBuildTags() {
        assertEquals(42, AppUpdateClient.parseBuildNumber("build-42"));
        assertEquals(42, AppUpdateClient.parseBuildNumber("release-build-42"));
        assertEquals(0, AppUpdateClient.parseBuildNumber("build-0"));
        assertEquals(0, AppUpdateClient.parseBuildNumber("build-not-a-number"));
    }

    @Test
    public void parsesSemanticTagsWithComparableVersionCode() {
        assertEquals(80201, AppUpdateClient.parseBuildNumber("v8.2.1"));
        assertEquals(80201, AppUpdateClient.parseBuildNumber("8.2.1-beta"));
        assertEquals(0, AppUpdateClient.parseBuildNumber("v0.100.1"));
        assertEquals(0, AppUpdateClient.parseBuildNumber("v0.8"));
    }
}
