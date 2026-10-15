package cc.ecisr.jyutdict.utils;

/** One selectable reading, with its dictionary location rather than a claimed recording accent. */
public record PronunciationOption(String location, String reading) {}
