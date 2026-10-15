package cc.ecisr.jyutdict;

import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_CHARA;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_MODE_MASK;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_PRON;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_SHEET;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cc.ecisr.jyutdict.search.DictionaryRecordStore;
import cc.ecisr.jyutdict.utils.ChineseTextConverter;

/** Adapter for persisted search history and favorite snapshots. */
final class DictionaryRecordsAdapter extends RecyclerView.Adapter<DictionaryRecordsAdapter.Holder> {
    interface Listener {
        void onOpenHistory(DictionaryRecordStore.HistoryEntry entry);

        void onOpenFavorite(DictionaryRecordStore.FavoriteEntry entry);

        void onRemoveHistory(DictionaryRecordStore.HistoryEntry entry);

        void onRemoveFavorite(DictionaryRecordStore.FavoriteEntry entry);
    }

    static final class ListData {
        final boolean favorites;
        final List<DictionaryRecordStore.HistoryEntry> history;
        final List<DictionaryRecordStore.FavoriteEntry> favoriteEntries;

        private ListData(boolean favorites, List<DictionaryRecordStore.HistoryEntry> history,
                         List<DictionaryRecordStore.FavoriteEntry> favoriteEntries) {
            this.favorites = favorites;
            this.history = history;
            this.favoriteEntries = favoriteEntries;
        }

        static ListData history(List<DictionaryRecordStore.HistoryEntry> entries) {
            return new ListData(false, entries, Collections.emptyList());
        }

        static ListData favorites(List<DictionaryRecordStore.FavoriteEntry> entries) {
            return new ListData(true, Collections.emptyList(), entries);
        }
    }

    private final DictionaryRecordStore recordStore;
    private final Listener listener;
    private ListData data = ListData.history(Collections.emptyList());

    DictionaryRecordsAdapter(DictionaryRecordStore recordStore, Listener listener) {
        this.recordStore = recordStore;
        this.listener = listener;
    }

    void submit(ListData data) {
        this.data = data == null ? ListData.history(Collections.emptyList()) : data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dictionary_record, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        if (data.favorites) {
            holder.bindFavorite(data.favoriteEntries.get(position));
        } else {
            holder.bindHistory(data.history.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return data.favorites ? data.favoriteEntries.size() : data.history.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView reading;
        final TextView definition;
        final TextView meta;
        final ImageButton action;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.record_title);
            reading = itemView.findViewById(R.id.record_reading);
            definition = itemView.findViewById(R.id.record_definition);
            meta = itemView.findViewById(R.id.record_meta);
            action = itemView.findViewById(R.id.record_action);
        }

        void bindHistory(DictionaryRecordStore.HistoryEntry entry) {
            title.setText(entry.query);
            reading.setVisibility(View.GONE);
            definition.setVisibility(View.GONE);
            meta.setText(joinMeta(itemView.getContext(),
                    modeLabel(itemView.getContext(), entry.mode),
                    resultLabel(itemView.getContext(), entry.resultCount),
                    formatDate(entry.searchedAt)));
            action.setImageResource(R.drawable.ic_trash_24);
            action.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    itemView.getContext(), R.color.colorGray)));
            setActionDescription(itemView.getContext().getString(R.string.records_remove_history));
            itemView.setOnClickListener(view -> listener.onOpenHistory(entry));
            action.setOnClickListener(view -> listener.onRemoveHistory(entry));
        }

        void bindFavorite(DictionaryRecordStore.FavoriteEntry entry) {
            String headword = ChineseTextConverter.display(entry.headword);
            String readings = ChineseTextConverter.display(entry.readings);
            title.setText(headword.isEmpty() ? readings : headword);
            reading.setText(readings);
            reading.setVisibility(readings.isEmpty() ? View.GONE : View.VISIBLE);
            String definitions = ChineseTextConverter.display(entry.definitions);
            definition.setText(definitions);
            definition.setVisibility(definitions.isEmpty() ? View.GONE : View.VISIBLE);
            meta.setText(joinMeta(itemView.getContext(),
                    ChineseTextConverter.display(entry.source),
                    itemView.getContext().getString(R.string.records_saved_at,
                            formatDate(entry.savedAt))));
            action.setImageResource(R.drawable.ic_bookmark_filled_24);
            action.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    itemView.getContext(), R.color.colorPrimary)));
            setActionDescription(itemView.getContext().getString(R.string.records_remove_favorite));
            itemView.setOnClickListener(view -> listener.onOpenFavorite(entry));
            action.setOnClickListener(view -> listener.onRemoveFavorite(entry));
        }

        private void setActionDescription(String description) {
            action.setContentDescription(description);
            TooltipCompat.setTooltipText(action, description);
        }
    }

    static String modeLabel(Context context, int mode) {
        switch (mode & QUERYING_MODE_MASK) {
            case QUERYING_PRON:
                return context.getString(R.string.records_mode_pron);
            case QUERYING_SHEET:
                return context.getString(R.string.records_mode_sheet);
            case QUERYING_CHARA:
            default:
                return context.getString(R.string.records_mode_chara);
        }
    }

    private static String resultLabel(Context context, int resultCount) {
        return resultCount < 0
                ? context.getString(R.string.records_result_count_unknown)
                : context.getString(R.string.records_result_count, resultCount);
    }

    private static String joinMeta(Context context, String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            if (result.length() > 0) result.append(" · ");
            result.append(value);
        }
        return result.toString();
    }

    private static String formatDate(long timestamp) {
        if (timestamp <= 0L) return "";
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                Locale.getDefault()).format(new Date(timestamp));
    }
}
