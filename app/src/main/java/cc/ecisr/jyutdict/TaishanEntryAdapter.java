package cc.ecisr.jyutdict;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.ImageButton;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.TooltipCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import cc.ecisr.jyutdict.databinding.ItemTaishanEntryBinding;
import cc.ecisr.jyutdict.utils.ChineseTextConverter;
import cc.ecisr.jyutdict.utils.PronunciationPlayer;
import java.util.List;

class TaishanEntryAdapter extends ListAdapter<TaishanEntry, TaishanEntryAdapter.ViewHolder> {
    interface AudioActions {
        void play(TaishanEntry entry);
        void manage(TaishanEntry entry);
        void reference(TaishanEntry entry);
        boolean isLookingUp(TaishanEntry entry);
        CharSequence onlineStatus(TaishanEntry entry);
    }

    private static final Object AUDIO = new Object();
    private final TaishanRecordingStore recordings;
    private final AudioActions actions;
    private final PronunciationPlayer.Listener playbackListener = (key, state) -> refreshAudio();
    private static final DiffUtil.ItemCallback<TaishanEntry> DIFF =
            new DiffUtil.ItemCallback<TaishanEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull TaishanEntry oldItem,
                                               @NonNull TaishanEntry newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull TaishanEntry oldItem,
                                                  @NonNull TaishanEntry newItem) {
                    return oldItem.headword.equals(newItem.headword)
                            && oldItem.ipa.equals(newItem.ipa)
                            && oldItem.definition.equals(newItem.definition)
                            && oldItem.category.equals(newItem.category)
                            && oldItem.subcategory.equals(newItem.subcategory)
                            && oldItem.bookPage == newItem.bookPage;
                }
            };

    TaishanEntryAdapter(TaishanRecordingStore recordings, AudioActions actions) {
        super(DIFF);
        this.recordings = recordings;
        this.actions = actions;
        PronunciationPlayer.addListener(playbackListener);
    }

    void refreshAudio() {
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount(), AUDIO);
    }

    void dispose() {
        PronunciationPlayer.removeListener(playbackListener);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new ViewHolder(ItemTaishanEntryBinding.inflate(inflater, parent, false), actions);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
        holder.bindAudio(getItem(position), recordings, actions);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(AUDIO)) holder.bindAudio(getItem(position), recordings, actions);
        else onBindViewHolder(holder, position);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemTaishanEntryBinding binding;
        private final ImageButton play;
        private final View loading;
        private TaishanEntry entry;

        ViewHolder(@NonNull ItemTaishanEntryBinding binding, AudioActions actions) {
            super(binding.getRoot());
            this.binding = binding;
            play = itemView.findViewById(R.id.pronunciation_play);
            loading = itemView.findViewById(R.id.pronunciation_loading);
            ColorStateList tint = ColorStateList.valueOf(themeColor(
                    itemView.getContext(), R.attr.clockAccentColor));
            play.setImageTintList(tint);
            binding.taishanAudioManage.setImageTintList(tint);
            play.setOnClickListener(view -> actions.play(entry));
            binding.taishanAudioManage.setOnClickListener(view -> actions.manage(entry));
            binding.taishanAudioStatus.setOnClickListener(view -> actions.reference(entry));
            TooltipCompat.setTooltipText(binding.taishanAudioManage,
                    itemView.getContext().getString(R.string.taishan_audio_manage));
        }

        void bindAudio(TaishanEntry entry, TaishanRecordingStore recordings, AudioActions actions) {
            this.entry = entry;
            boolean available = recordings.contains(entry);
            boolean searching = actions.isLookingUp(entry);
            boolean active = searching || PronunciationPlayer.isActive(TaishanRecordingStore.key(entry));
            boolean busy = searching || (active && PronunciationPlayer.isLoading());
            boolean enabled = available || !entry.headword.isEmpty();
            play.setEnabled(enabled);
            play.setAlpha(enabled ? 1f : 0.35f);
            play.setImageResource(active ? R.drawable.ic_stop_24 : R.drawable.ic_volume_24);
            play.setImageAlpha(busy ? 0 : 255);
            loading.setVisibility(busy ? View.VISIBLE : View.GONE);
            int label = active ? R.string.pron_audio_stop
                    : available ? R.string.taishan_audio_play : enabled ? R.string.taishan_audio_lookup : R.string.taishan_audio_missing;
            String description = itemView.getContext().getString(label);
            play.setContentDescription(description);
            TooltipCompat.setTooltipText(play, description);
            binding.taishanAudioStatus.setText(available ? itemView.getContext().getString(R.string.taishan_audio_local)
                    : enabled ? actions.onlineStatus(entry) : itemView.getContext().getString(R.string.taishan_audio_missing));
            binding.taishanAudioStatus.setEnabled(enabled && !available);
            binding.taishanAudioStatus.setClickable(enabled && !available);
            binding.taishanAudioStatus.setFocusable(enabled && !available);
            binding.taishanAudioStatus.setTextColor(themeColor(itemView.getContext(),
                    enabled && !available ? R.attr.clockAccentColor : R.attr.clockTextColorDim));
            TooltipCompat.setTooltipText(binding.taishanAudioStatus, enabled && !available
                    ? itemView.getContext().getString(R.string.taishan_audio_reference_view) : null);
        }

        void bind(TaishanEntry entry) {
            Context context = binding.getRoot().getContext();
            if (entry.headword.isEmpty()) {
                binding.taishanHeadword.setText(R.string.taishan_missing_headword);
                binding.taishanHeadword.setTextColor(themeColor(context, R.attr.clockTextColorDim));
            } else {
                binding.taishanHeadword.setText(ChineseTextConverter.display(entry.headword));
                binding.taishanHeadword.setTextColor(themeColor(context, R.attr.clockTextColor));
            }
            binding.taishanIpa.setText(entry.ipa);
            binding.taishanDefinition.setText(ChineseTextConverter.display(entry.definition));
            binding.taishanPage.setText(context.getString(
                    R.string.taishan_page, entry.bookPage));

            String category = ChineseTextConverter.display(entry.category);
            String subcategory = ChineseTextConverter.display(entry.subcategory);
            if (subcategory.isEmpty()) {
                binding.taishanCategory.setText(category);
            } else {
                binding.taishanCategory.setText(context.getString(
                        R.string.taishan_category_value, category, subcategory));
            }
        }

        private static int themeColor(Context context, int attribute) {
            TypedValue value = new TypedValue();
            if (!context.getTheme().resolveAttribute(attribute, value, true)) {
                return ContextCompat.getColor(context, R.color.colorBlack);
            }
            return value.data;
        }
    }
}
