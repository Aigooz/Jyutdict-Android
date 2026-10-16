package cc.ecisr.jyutdict;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cc.ecisr.jyutdict.utils.ChineseTextConverter;
import cc.ecisr.jyutdict.utils.PronunciationPlayer;

final class TaishanOnlineAdapter extends RecyclerView.Adapter<TaishanOnlineAdapter.Holder> {
    private final List<TaishanAudioSource.DictionaryEntry> entries = new ArrayList<>();
    private final PronunciationPlayer.Listener playbackListener = (key, state) -> notifyDataSetChanged();
    private TaishanChineseTranslation translator;
    private final java.util.Map<String, String> translated = new java.util.HashMap<>();
    private final java.util.Set<String> expanded = new java.util.HashSet<>();
    private final java.util.Set<String> translating = new java.util.HashSet<>();
    private final java.util.Set<String> failed = new java.util.HashSet<>();
    private AlertDialog translationNotice;

    TaishanOnlineAdapter() {
        PronunciationPlayer.addListener(playbackListener);
    }

    void submit(List<TaishanAudioSource.DictionaryEntry> values) {
        entries.clear();
        entries.addAll(values);
        notifyDataSetChanged();
    }

    void dispose() {
        PronunciationPlayer.removeListener(playbackListener);
        if (translator != null) translator.close();
        if (translationNotice != null) translationNotice.dismiss();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_taishan_online_entry, parent, false);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TaishanAudioSource.DictionaryEntry entry = entries.get(position);
        holder.headword.setText(ChineseTextConverter.display(entry.headword));
        holder.ipa.setText("[" + entry.ipa + "]");
        String equivalents = holder.itemView.getContext().getString(
                R.string.taishan_online_equivalents, entry.cantonese, entry.mandarin);
        holder.equivalents.setText(ChineseTextConverter.display(equivalents));
        holder.equivalents.setVisibility(entry.cantonese.isEmpty() && entry.mandarin.isEmpty()
                ? View.GONE : View.VISIBLE);
        Context context = holder.itemView.getContext();
        String sourceText = TaishanChineseTranslation.sourceText(entry.english, entry.definition);
        boolean needsTranslation = TaishanChineseTranslation.needsTranslation(sourceText);
        String chinese = translated.get(sourceText);
        String meaning = TaishanChineseTranslation.chineseMeaning(entry.mandarin, entry.cantonese);
        holder.english.setText(ChineseTextConverter.display(meaning.isEmpty()
                ? context.getString(R.string.taishan_chinese_missing) : meaning));
        holder.english.setVisibility(View.VISIBLE);
        holder.definition.setText(ChineseTextConverter.display(chinese != null
                ? context.getString(R.string.taishan_chinese_machine, chinese)
                : needsTranslation ? "" : sourceText));
        holder.definition.setVisibility(holder.definition.length() == 0 ? View.GONE : View.VISIBLE);
        holder.original.setText(sourceText);
        holder.original.setVisibility(expanded.contains(sourceText) ? View.VISIBLE : View.GONE);
        holder.originalToggle.setVisibility(sourceText.isEmpty() || !needsTranslation ? View.GONE : View.VISIBLE);
        holder.originalToggle.setText(expanded.contains(sourceText)
                ? R.string.taishan_original_hide : R.string.taishan_original_show);
        holder.originalToggle.setOnClickListener(view -> {
            if (!expanded.remove(sourceText)) expanded.add(sourceText);
            notifySourceChanged(sourceText);
        });
        holder.translate.setVisibility(needsTranslation && chinese == null ? View.VISIBLE : View.GONE);
        holder.translate.setEnabled(!translating.contains(sourceText));
        holder.translate.setText(translating.contains(sourceText) ? R.string.taishan_chinese_loading
                : failed.contains(sourceText) ? R.string.taishan_chinese_retry : R.string.taishan_chinese_translate);
        holder.translate.setOnClickListener(view -> {
            if (context.getSharedPreferences("taishan_translation", Context.MODE_PRIVATE)
                    .getBoolean("consent", false)) {
                translate(context, sourceText);
                return;
            }
            translationNotice = new AlertDialog.Builder(context)
                    .setTitle(R.string.taishan_chinese_translate)
                    .setMessage(R.string.taishan_chinese_notice)
                    .setPositiveButton(R.string.button_confirm, (dialog, which) -> {
                        context.getSharedPreferences("taishan_translation", Context.MODE_PRIVATE)
                                .edit().putBoolean("consent", true).apply();
                        translate(context, sourceText);
                    })
                    .setNegativeButton(R.string.button_cancel, null).show();
        });
        holder.source.setText(R.string.taishan_online_credit);
        String key = "taishan-online:" + entry.headword + ":" + entry.ipa + ":" + entry.audioUrl;
        boolean active = PronunciationPlayer.isActive(key);
        holder.play.setImageResource(active ? R.drawable.ic_stop_24 : R.drawable.ic_volume_24);
        holder.play.setEnabled(entry.audioUrl != null);
        holder.play.setAlpha(entry.audioUrl == null ? 0.38f : 1f);
        holder.play.setContentDescription(holder.itemView.getContext().getString(active
                ? R.string.pron_audio_stop : R.string.taishan_online_play));
        holder.play.setOnClickListener(view -> {
            if (PronunciationPlayer.isActive(key)) PronunciationPlayer.stop();
            else if (entry.audioUrl != null) {
                PronunciationPlayer.playTaishanReference(view.getContext(), key, entry.audioUrl);
            }
        });
    }

    @Override
    public int getItemCount() { return entries.size(); }

    private void translate(Context context, String text) {
        if (!translating.add(text)) return;
        failed.remove(text);
        notifySourceChanged(text);
        if (translator == null) translator = new TaishanChineseTranslation(context);
        translator.translate(text, chinese -> {
            translating.remove(text);
            if (chinese == null) failed.add(text);
            else translated.put(text, chinese);
            notifySourceChanged(text);
        });
    }

    private void notifySourceChanged(String text) {
        for (int index = 0; index < entries.size(); index++) {
            TaishanAudioSource.DictionaryEntry entry = entries.get(index);
            if (text.equals(TaishanChineseTranslation.sourceText(entry.english, entry.definition))) {
                notifyItemChanged(index);
            }
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView headword;
        final TextView ipa;
        final TextView equivalents;
        final TextView english;
        final TextView definition;
        final TextView source;
        final ImageButton play;
        final TextView original;
        final TextView originalToggle;
        final TextView translate;

        Holder(View itemView) {
            super(itemView);
            headword = itemView.findViewById(R.id.online_headword);
            ipa = itemView.findViewById(R.id.online_ipa);
            equivalents = itemView.findViewById(R.id.online_equivalents);
            english = itemView.findViewById(R.id.online_english);
            definition = itemView.findViewById(R.id.online_definition);
            source = itemView.findViewById(R.id.online_source);
            play = itemView.findViewById(R.id.online_play);
            original = itemView.findViewById(R.id.online_original);
            originalToggle = itemView.findViewById(R.id.online_original_toggle);
            translate = itemView.findViewById(R.id.online_translate);
            TypedValue value = new TypedValue();
            if (itemView.getContext().getTheme().resolveAttribute(R.attr.clockAccentColor, value, true)) {
                play.setImageTintList(ColorStateList.valueOf(value.data));
            } else {
                play.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                        itemView.getContext(), R.color.colorPrimary)));
            }
        }
    }
}
