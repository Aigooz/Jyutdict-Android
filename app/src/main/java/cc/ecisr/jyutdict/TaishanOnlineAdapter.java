package cc.ecisr.jyutdict;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

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
        holder.equivalents.setText(equivalents);
        holder.equivalents.setVisibility(entry.cantonese.isEmpty() && entry.mandarin.isEmpty()
                ? View.GONE : View.VISIBLE);
        holder.english.setText(entry.english);
        holder.english.setVisibility(entry.english.isEmpty() ? View.GONE : View.VISIBLE);
        holder.definition.setText(ChineseTextConverter.display(entry.definition));
        holder.definition.setVisibility(entry.definition.isEmpty() ? View.GONE : View.VISIBLE);
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

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView headword;
        final TextView ipa;
        final TextView equivalents;
        final TextView english;
        final TextView definition;
        final TextView source;
        final ImageButton play;

        Holder(View itemView) {
            super(itemView);
            headword = itemView.findViewById(R.id.online_headword);
            ipa = itemView.findViewById(R.id.online_ipa);
            equivalents = itemView.findViewById(R.id.online_equivalents);
            english = itemView.findViewById(R.id.online_english);
            definition = itemView.findViewById(R.id.online_definition);
            source = itemView.findViewById(R.id.online_source);
            play = itemView.findViewById(R.id.online_play);
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
