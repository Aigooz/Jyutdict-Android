package cc.ecisr.jyutdict;

import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_CHARA;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_MODE_MASK;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_PRON;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_SHEET;

import android.content.Context;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.content.res.ColorStateList;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import cc.ecisr.jyutdict.search.DictionaryRecordStore;
import cc.ecisr.jyutdict.search.SearchQueryNormalizer;

import cc.ecisr.jyutdict.utils.MotionUtil;
import cc.ecisr.jyutdict.utils.PronunciationOption;
import cc.ecisr.jyutdict.utils.PronunciationPlayer;
import cc.ecisr.jyutdict.widget.CharacterHeaderSpan;
import cc.ecisr.jyutdict.widget.HorizontalDividerSpan;
import cc.ecisr.jyutdict.widget.SelectableTextView;

public class ResultItemAdapter extends RecyclerView.Adapter<ResultItemAdapter.LinearViewHolder> {
    private final ArrayList<ResultInfo> items = new ArrayList<>();
    private static final Object PLAYBACK = new Object();
    private static final Object FAVORITE = new Object();
    private DictionaryRecordStore recordStore;
    private int receivedMode;
    private final Set<String> favoriteKeys = new LinkedHashSet<>();
    private final PronunciationPlayer.Listener playbackListener = (key, state) -> {
        if (!items.isEmpty()) notifyItemRangeChanged(0, items.size(), PLAYBACK);
    };

    ResultItemAdapter() {
        PronunciationPlayer.addListener(playbackListener);
    }

    void configureRecords(DictionaryRecordStore store, int mode) {
        recordStore = store;
        receivedMode = mode;
        refreshFavoriteKeys();
    }

    void refreshFavoriteKeys() {
        favoriteKeys.clear();
        if (recordStore == null) return;
        for (DictionaryRecordStore.FavoriteEntry entry : recordStore.favorites()) {
            favoriteKeys.add(entry.key);
        }
        if (!items.isEmpty()) notifyItemRangeChanged(0, items.size(), FAVORITE);
    }

    void dispose() {
        PronunciationPlayer.removeListener(playbackListener);
    }

    @NonNull
    @Override
    public LinearViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == ResultInfo.TYPE_SHEET
                ? R.layout.layout_result_list_item_sheet : R.layout.layout_result_list_item;
        return new LinearViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull LinearViewHolder holder, int position) {
        ResultInfo item = items.get(position);
        holder.currentItem = item;
        holder.bindPronunciation();
        holder.bindFavorite();
        holder.collapseAnnotation(false);

        if (item.type == ResultInfo.TYPE_SHEET) {
            holder.tvCharaHeader.setSelectableText(item.chara);
            holder.tvCharaInfo.setSelectableText(item.leftMiddle);
            holder.tvCharaExtra.setSelectableText(item.leftBottom);
            holder.tvRightTop.setSelectableText(item.rightTop);
            holder.tvRightTop.setVisibility(View.GONE);
            holder.tvRightBottom.setSelectableText(joinTextSections(item.rightTop, item.rightBottom));
            holder.tvRightBottom.setVisibility(empty(item.rightTop) && empty(item.rightBottom)
                    ? View.GONE : View.VISIBLE);
            holder.lyChara.setVisibility(!empty(item.chara) || !empty(item.leftMiddle)
                    ? View.VISIBLE : View.GONE);
            holder.tvCharaInfo.setVisibility(empty(item.leftMiddle) ? View.GONE : View.VISIBLE);
            holder.tvCharaExtra.setVisibility(empty(item.leftBottom) ? View.GONE : View.VISIBLE);
            holder.contentDivider.setVisibility(View.GONE);
        } else {
            holder.tvRightBottom.setSelectableText(formatGeneralEntry(holder.itemView.getContext(),
                    item.chara, item.leftBottom, item.rightTop, item.rightBottom));
            holder.tvRightBottom.setVisibility(empty(item.chara) && empty(item.leftBottom)
                    && empty(item.rightTop) && empty(item.rightBottom)
                    ? View.GONE : View.VISIBLE);
        }
        holder.tvRightBottom.setOnAnnotationClickListener(holder::toggleAnnotation);

        holder.setOnNonLinkClickListener(null);
        if (item.type == ResultInfo.TYPE_SHEET) {
            holder.tvRightBottom.setOnNonLinkClickListener(null);
        }
        holder.itemView.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
    }

    @Override
    public void onBindViewHolder(@NonNull LinearViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PLAYBACK)) {
            holder.bindPronunciation();
        } else if (!payloads.isEmpty() && payloads.contains(FAVORITE)) {
            holder.bindFavorite();
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    void replaceItems(List<ResultInfo> newItems) {
        items.clear();
        items.addAll(newItems);
        refreshFavoriteKeys();
    }

    ResultInfo getItem(int position) {
        return items.get(position);
    }

    private static Spanned joinTextSections(Spanned first, Spanned second) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        if (!empty(first)) result.append(first);
        if (!empty(first) && !empty(second)) result.append("\n\n");
        if (!empty(second)) result.append(second);
        return result;
    }

    private static Spanned formatGeneralEntry(Context context, Spanned header, Spanned extra,
                                              Spanned wanshyu, Spanned location) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        boolean hasHeader = !empty(header), hasExtra = !empty(extra);
        boolean hasWanshyu = !empty(wanshyu), hasLocation = !empty(location);
        boolean hasTop = hasHeader || hasExtra || hasWanshyu;

        if (hasHeader) {
            CharacterHeaderSpan headerSpan = new CharacterHeaderSpan(
                    context, !hasExtra && !hasWanshyu);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13,
                    context.getResources().getDisplayMetrics()));
            int headerWidth = headerSpan.getTotalWidth(
                    paint, header.toString(), 0, header.length());
            int headerStart = result.length();
            result.append(header);
            span(result, headerSpan, headerStart, result.length());

            if (hasExtra) {
                int extraStart = result.length();
                result.append(extra);
                span(result, new ForegroundColorSpan(ContextCompat.getColor(
                        context, R.color.colorPrimary)), extraStart, result.length());
                span(result, new LeadingMarginSpan.Standard(0, headerWidth),
                        headerStart, result.length());
                if (hasWanshyu) {
                    appendWanshyuParagraphs(result, wanshyu, headerStart, headerWidth, false);
                }
            } else if (hasWanshyu) {
                appendWanshyuParagraphs(result, wanshyu, headerStart, headerWidth, true);
            }
        } else {
            if (hasExtra) {
                int start = result.length();
                result.append(extra);
                span(result, new ForegroundColorSpan(ContextCompat.getColor(
                        context, R.color.colorPrimary)), start, result.length());
            }
            if (hasWanshyu) {
                if (result.length() > 0) result.append("\n");
                result.append(wanshyu);
            }
        }

        if (hasTop && hasLocation) {
            result.append("\n\u200B\n");
            span(result, new HorizontalDividerSpan(context),
                    result.length() - 2, result.length() - 1);
        }
        if (hasLocation) result.append(location);
        return result;
    }

    private static void appendWanshyuParagraphs(SpannableStringBuilder result, Spanned source,
                                                int headerStart, int headerWidth,
                                                boolean firstFollowsHeader) {
        String plain = source.toString();
        int cursor = 0;
        boolean first = true;
        while (cursor < source.length()) {
            int newline = plain.indexOf('\n', cursor);
            int end = newline < 0 ? source.length() : newline;
            CharSequence paragraph = source.subSequence(cursor, end);
            if (first && firstFollowsHeader) {
                result.append(paragraph);
                span(result, new LeadingMarginSpan.Standard(0, headerWidth),
                        headerStart, result.length());
                first = false;
            } else {
                result.append("\n");
                int start = result.length();
                result.append(paragraph);
                span(result, new LeadingMarginSpan.Standard(headerWidth, headerWidth),
                        start, result.length());
            }
            cursor = end + 1;
        }
    }

    private static void span(SpannableStringBuilder text, Object span, int start, int end) {
        text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static boolean empty(CharSequence text) {
        return text == null || text.length() == 0;
    }

    private static void appendLine(StringBuilder output, CharSequence text) {
        if (!empty(text)) output.append(text).append("\n");
    }

    private static String favoriteKey(int mode, ResultInfo item) {
        String headword = empty(item.chara) ? "" : item.chara.toString();
        String readings = favoriteReadings(item);
        return mode + "\u0000" + SearchQueryNormalizer.normalize(headword)
                + "\u0000" + SearchQueryNormalizer.normalize(readings);
    }

    private static String favoriteReadings(ResultInfo item) {
        StringBuilder result = new StringBuilder();
        appendLine(result, item.leftMiddle);
        appendLine(result, item.leftBottom);
        return result.toString().trim();
    }

    private static String favoriteDefinitions(ResultInfo item) {
        StringBuilder result = new StringBuilder();
        appendLine(result, item.rightTop);
        appendLine(result, item.rightBottom);
        return result.toString().trim();
    }

    private static String favoriteSource(int mode) {
        switch (mode & QUERYING_MODE_MASK) {
            case QUERYING_CHARA: return "通用字表";
            case QUERYING_PRON: return "通用字表 · 读音";
            case QUERYING_SHEET: return "泛粤字表";
            default: return "粤辞";
        }
    }

    public class LinearViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout lyChara;
        final View contentDivider, annotationContainer;
        final ImageButton pronunciationPlay, favoriteButton;
        final View pronunciationLoading;
        final SelectableTextView tvCharaHeader, tvCharaInfo, tvCharaExtra,
                tvRightTop, tvRightBottom, tvAnnotation;
        ResultInfo currentItem;
        String expandedAnnotation;

        LinearViewHolder(@NonNull View itemView) {
            super(itemView);
            lyChara = itemView.findViewById(R.id.item_chara);
            tvCharaHeader = itemView.findViewById(R.id.chara_header);
            tvCharaInfo = itemView.findViewById(R.id.chara_info);
            tvCharaExtra = itemView.findViewById(R.id.chara_extra);
            tvRightTop = itemView.findViewById(R.id.content_wanshyu);
            tvRightBottom = itemView.findViewById(R.id.content_location);
            contentDivider = itemView.findViewById(R.id.content_divider);
            annotationContainer = itemView.findViewById(R.id.sheet_annotation_container);
            tvAnnotation = itemView.findViewById(R.id.sheet_annotation_text);
            pronunciationPlay = itemView.findViewById(R.id.pronunciation_play);
            pronunciationLoading = itemView.findViewById(R.id.pronunciation_loading);
            favoriteButton = itemView.findViewById(R.id.result_favorite);
            pronunciationPlay.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    itemView.getContext(), R.color.colorPrimary)));
            pronunciationPlay.setOnClickListener(view -> playPronunciation());
            if (favoriteButton != null) {
                favoriteButton.setOnClickListener(view -> toggleFavorite());
            }
            if (annotationContainer != null) {
                annotationContainer.setOnClickListener(view -> collapseAnnotation(true));
            }
            if (tvAnnotation != null) {
                tvAnnotation.setOnClickListener(view -> collapseAnnotation(true));
            }
        }

        void bindPronunciation() {
            boolean available = !currentItem.pronunciations.isEmpty();
            boolean active = hasActivePronunciation();
            boolean loading = active && PronunciationPlayer.isLoading();
            pronunciationPlay.setEnabled(available);
            pronunciationPlay.setAlpha(available ? 1f : 0.35f);
            pronunciationPlay.setImageResource(active ? R.drawable.ic_stop_24 : R.drawable.ic_volume_24);
            pronunciationPlay.setImageAlpha(loading ? 0 : 255);
            pronunciationLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            int label = active ? R.string.pron_audio_stop
                    : available ? R.string.pron_audio_reference : R.string.pron_audio_unavailable;
            String description = itemView.getContext().getString(label);
            pronunciationPlay.setContentDescription(description);
            TooltipCompat.setTooltipText(pronunciationPlay, description);
        }

        void bindFavorite() {
            if (favoriteButton == null) return;
            boolean favorite = recordStore != null
                    && favoriteKeys.contains(favoriteKey(receivedMode, currentItem));
            favoriteButton.setImageResource(favorite
                    ? R.drawable.ic_bookmark_filled_24 : R.drawable.ic_bookmark_24);
            favoriteButton.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    itemView.getContext(), favorite ? R.color.colorPrimary : R.color.colorGray)));
            String description = itemView.getContext().getString(favorite
                    ? R.string.records_favorite_remove : R.string.records_favorite_add);
            favoriteButton.setContentDescription(description);
            TooltipCompat.setTooltipText(favoriteButton, description);
        }

        private void toggleFavorite() {
            if (recordStore == null) return;
            String key = favoriteKey(receivedMode, currentItem);
            String headword = getChara();
            boolean added = recordStore.toggleFavorite(
                    DictionaryRecordStore.FavoriteEntry.createWithKey(
                            key, headword, favoriteReadings(currentItem),
                            favoriteDefinitions(currentItem),
                            favoriteSource(receivedMode), receivedMode,
                            System.currentTimeMillis()));
            favoriteKeys.clear();
            for (DictionaryRecordStore.FavoriteEntry entry : recordStore.favorites()) {
                favoriteKeys.add(entry.key);
            }
            int position = getAbsoluteAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                RecyclerView.Adapter<?> adapter = getBindingAdapter();
                if (adapter instanceof ResultItemAdapter) {
                    ((ResultItemAdapter) adapter).notifyItemChanged(position, FAVORITE);
                }
            }
            Toast.makeText(itemView.getContext(), added
                    ? R.string.records_favorite_added : R.string.records_favorite_removed,
                    Toast.LENGTH_SHORT).show();
        }

        private boolean hasActivePronunciation() {
            for (PronunciationOption option : currentItem.pronunciations) {
                if (PronunciationPlayer.isActive(PronunciationPlayer.keyForReading(option.reading()))) return true;
            }
            return false;
        }

        private void playPronunciation() {
            if (hasActivePronunciation()) {
                PronunciationPlayer.stop();
                return;
            }
            List<PronunciationOption> options = currentItem.pronunciations;
            if (options.isEmpty()) return;
            Context context = itemView.getContext();
            if (options.size() == 1) {
                PronunciationPlayer.play(context, options.get(0).reading());
                return;
            }
            String[] labels = new String[options.size()];
            for (int i = 0; i < labels.length; i++) {
                PronunciationOption option = options.get(i);
                String location = option.location().isEmpty()
                        ? context.getString(R.string.select_drop_down_standard) : option.location();
                labels[i] = location + " · " + option.reading();
            }
            new AlertDialog.Builder(context)
                    .setTitle(R.string.pron_audio_reference)
                    .setItems(labels, (dialog, which) -> PronunciationPlayer.play(context, options.get(which).reading()))
                    .setNegativeButton(R.string.button_cancel, null)
                    .show();
        }

        void setOnNonLinkClickListener(View.OnClickListener listener) {
            SelectableTextView[] views = {tvCharaHeader, tvCharaInfo, tvCharaExtra,
                    tvRightTop, tvRightBottom};
            for (SelectableTextView view : views) {
                if (view != null) view.setOnNonLinkClickListener(listener);
            }
        }

        ResultInfo.CommentTarget getCommentTarget() {
            return currentItem.commentTarget;
        }

        CharSequence getWanshyuText() {
            return currentItem.rightTop.toString();
        }

        void toggleAnnotation(String annotation) {
            if (annotationContainer == null || tvAnnotation == null) return;
            if (annotationContainer.getVisibility() == View.VISIBLE
                    && annotation.equals(expandedAnnotation)) {
                collapseAnnotation(true);
                return;
            }
            expandedAnnotation = annotation;
            tvAnnotation.setSelectableText(annotation);
            beginAnnotationTransition();
            annotationContainer.setVisibility(View.VISIBLE);
        }

        void collapseAnnotation(boolean animate) {
            expandedAnnotation = null;
            if (animate) beginAnnotationTransition();
            if (annotationContainer != null) annotationContainer.setVisibility(View.GONE);
            if (tvAnnotation != null) tvAnnotation.setSelectableText("");
        }

        private void beginAnnotationTransition() {
            if (itemView.getParent() instanceof ViewGroup) {
                MotionUtil.beginLayoutTransition((ViewGroup) itemView.getParent());
            } else if (itemView instanceof ViewGroup) {
                MotionUtil.beginLayoutTransition((ViewGroup) itemView);
            }
        }

        String getChara() {
            return empty(currentItem.chara) ? "" : currentItem.chara.toString();
        }

        String printContent() {
            StringBuilder result = new StringBuilder();
            appendLine(result, currentItem.chara);
            appendLine(result, currentItem.leftMiddle);
            appendLine(result, currentItem.leftBottom);
            appendLine(result, currentItem.rightTop);
            if (!empty(currentItem.rightBottom)) {
                if (result.length() > 0) result.append("\n");
                appendLine(result, currentItem.rightBottom);
            }
            return result.toString();
        }

    }

    static class ResultInfo {
        static final int TYPE_GENERAL = 0, TYPE_SHEET = 1;
        final Spanned chara, leftMiddle, leftBottom, rightTop, rightBottom;
        final int type;
        final CommentTarget commentTarget;
        List<PronunciationOption> pronunciations = Collections.emptyList();

        ResultInfo(Spanned chara, Spanned leftMiddle, Spanned leftBottom,
                   Spanned rightTop, Spanned rightBottom, int type,
                   String commentType, String commentTarget) {
            this.chara = chara;
            this.leftMiddle = leftMiddle;
            this.leftBottom = leftBottom;
            this.rightTop = rightTop;
            this.rightBottom = rightBottom;
            this.type = type;
            this.commentTarget = commentType == null || commentTarget == null
                    ? null : new CommentTarget(commentType, commentTarget);
        }

        static final class CommentTarget {
            final String type, target;
            int count;
            boolean countLoaded;

            CommentTarget(String type, String target) {
                this.type = type;
                this.target = target;
            }
        }
    }
}
