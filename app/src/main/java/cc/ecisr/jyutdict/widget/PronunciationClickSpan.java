package cc.ecisr.jyutdict.widget;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import cc.ecisr.jyutdict.utils.PronunciationPlayer;

/** Plays a full dictionary reading using the Cantonese syllable reference corpus. */
public class PronunciationClickSpan extends ClickableSpan {
    private final String syllable;
    private final Integer color;

    public PronunciationClickSpan(String syllable) {
        this.syllable = syllable;
        this.color = null;
    }

    public PronunciationClickSpan(String syllable, int color) {
        this.syllable = syllable;
        this.color = color;
    }

    @Override
    public void onClick(View widget) {
        if (PronunciationPlayer.isActive(PronunciationPlayer.keyForReading(syllable))) {
            PronunciationPlayer.stop();
        } else {
            PronunciationPlayer.play(widget.getContext(), syllable);
        }
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        ds.setUnderlineText(false);
        if (color != null) ds.setColor(color);
    }
}
