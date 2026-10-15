package cc.ecisr.jyutdict;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import cc.ecisr.jyutdict.databinding.DialogTaishanAudioBinding;
import cc.ecisr.jyutdict.databinding.ItemTaishanAudioBinding;
import cc.ecisr.jyutdict.utils.ChineseTextConverter;
import cc.ecisr.jyutdict.utils.PronunciationPlayer;
import cc.ecisr.jyutdict.utils.ToastUtil;

/** Source-specific recordings are previews, not replacements for the printed dictionary. */
final class TaishanAudioDialog {
    private static final class Control {
        final String url;
        final ImageButton play;
        final View loading;
        final TextView status;
        final int matchLabel;

        Control(String url, View row, int matchLabel) {
            this.url = url;
            this.matchLabel = matchLabel;
            play = row.findViewById(R.id.pronunciation_play);
            loading = row.findViewById(R.id.pronunciation_loading);
            status = row.findViewById(R.id.audio_reading_match);
        }
    }

    static AlertDialog show(TaishanActivity activity, TaishanEntry entry,
                            List<TaishanAudioSource.Recording> recordings) {
        LayoutInflater inflater = activity.getLayoutInflater();
        DialogTaishanAudioBinding content = DialogTaishanAudioBinding.inflate(inflater);
        String key = TaishanRecordingStore.key(entry);
        content.audioBookReading.setText(activity.getString(R.string.taishan_audio_book_reading, entry.ipa));
        content.audioBookDefinition.setText(ChineseTextConverter.display(entry.definition));
        content.audioReferenceNotice.setText(recordings.isEmpty() ? R.string.taishan_audio_no_result
                : R.string.taishan_audio_reference_notice);
        List<Control> controls = new ArrayList<>();
        String[] selectedUrl = {null};
        for (TaishanAudioSource.Recording recording : recordings) {
            ItemTaishanAudioBinding row = ItemTaishanAudioBinding.inflate(inflater, content.audioCandidates, false);
            row.audioSourceReading.setText(activity.getString(R.string.taishan_audio_source_reading, recording.ipa));
            int matchLabel = recording.matchesReading(entry.rawIpa)
                    ? R.string.taishan_audio_reading_match : R.string.taishan_audio_reading_different;
            row.audioReadingMatch.setText(matchLabel);
            String meaning = ChineseTextConverter.display(recording.mandarin);
            if (!recording.english.isEmpty()) meaning += (meaning.isEmpty() ? "" : "\n") + recording.english;
            row.audioSourceMeaning.setText(meaning);
            row.audioSourceMeaning.setVisibility(meaning.isEmpty() ? View.GONE : View.VISIBLE);
            Control control = new Control(recording.url, row.getRoot(), matchLabel);
            control.play.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.colorPrimary)));
            control.play.setOnClickListener(view -> {
                if (recording.url.equals(selectedUrl[0]) && PronunciationPlayer.isActive(key)) {
                    PronunciationPlayer.stop();
                } else {
                    selectedUrl[0] = recording.url;
                    PronunciationPlayer.playTaishanReference(activity, key, recording.url);
                }
            });
            controls.add(control);
            content.audioCandidates.addView(row.getRoot());
        }
        PronunciationPlayer.Listener listener = new PronunciationPlayer.Listener() {
            @Override
            public void onPlaybackChanged(String playingKey, PronunciationPlayer.State state) {
                for (Control control : controls) {
                    boolean active = key.equals(playingKey) && control.url.equals(selectedUrl[0])
                            && state != PronunciationPlayer.State.IDLE;
                    boolean loading = active && state == PronunciationPlayer.State.LOADING;
                    control.play.setImageResource(active ? R.drawable.ic_stop_24 : R.drawable.ic_volume_24);
                    control.play.setImageAlpha(loading ? 0 : 255);
                    control.loading.setVisibility(loading ? View.VISIBLE : View.GONE);
                    if (loading) control.status.setText(control.matchLabel);
                    String label = activity.getString(active ? R.string.pron_audio_stop : R.string.pron_audio_play);
                    control.play.setContentDescription(label);
                    TooltipCompat.setTooltipText(control.play, label);
                }
            }

            @Override
            public void onReferenceFailed(String failedKey, String url, boolean unavailable) {
                if (!key.equals(failedKey)) return;
                for (Control control : controls) {
                    if (control.url.equals(url)) {
                        control.status.setText(unavailable
                                ? R.string.taishan_audio_source_unavailable : R.string.taishan_audio_playback_failed);
                        if (unavailable) control.play.setEnabled(false);
                    }
                }
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(ChineseTextConverter.display(entry.headword))
                .setView(content.getRoot())
                .setPositiveButton(R.string.taishan_audio_source_open, (which, button) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TaishanAudioSource.HOME)));
                    } catch (Exception exception) {
                        ToastUtil.msg(activity, activity.getString(R.string.version_link_unavailable));
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .create();
        dialog.setOnDismissListener(which -> {
            PronunciationPlayer.removeListener(listener);
            if (PronunciationPlayer.isActive(key)) PronunciationPlayer.stop();
        });
        dialog.setOnShowListener(which -> {
            int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.55f);
            int width = content.getRoot().getWidth();
            if (width <= 0) width = activity.getResources().getDisplayMetrics().widthPixels
                    - (int) (64 * activity.getResources().getDisplayMetrics().density);
            content.getRoot().measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (content.getRoot().getMeasuredHeight() > maxHeight) {
                content.getRoot().getLayoutParams().height = maxHeight;
                content.getRoot().requestLayout();
            }
        });
        dialog.show();
        PronunciationPlayer.addListener(listener);
        return dialog;
    }
}
