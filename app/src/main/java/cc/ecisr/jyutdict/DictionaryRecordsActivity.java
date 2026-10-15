package cc.ecisr.jyutdict;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.tabs.TabLayout;

import cc.ecisr.jyutdict.databinding.ActivityDictionaryRecordsBinding;
import cc.ecisr.jyutdict.search.DictionaryRecordStore;
import cc.ecisr.jyutdict.utils.ImmersiveBarUtil;
import cc.ecisr.jyutdict.utils.ThemeUtil;

/** Local history and bookmarked snapshots for dictionary results. */
public final class DictionaryRecordsActivity extends LocalizedActivity {
    static final String EXTRA_QUERY = "record_query";
    static final String EXTRA_MODE = "record_mode";
    static final String EXTRA_TAB = "record_tab";
    static final int TAB_HISTORY = 0;
    static final int TAB_FAVORITES = 1;

    private ActivityDictionaryRecordsBinding binding;
    private DictionaryRecordStore recordStore;
    private DictionaryRecordsAdapter adapter;
    private int selectedTab = TAB_HISTORY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeUtil.isNightMode(this) ? R.style.DarkTheme : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityDictionaryRecordsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ImmersiveBarUtil.setImmersiveBar(this, false, !ThemeUtil.isNightMode(this));
        ImmersiveBarUtil.applyToolbarInsets(binding.toolbar);
        ImmersiveBarUtil.applyBottomInsets(binding.recordsList);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recordStore = new DictionaryRecordStore(this);
        selectedTab = savedInstanceState == null
                ? getIntent().getIntExtra(EXTRA_TAB, TAB_HISTORY)
                : savedInstanceState.getInt("selected_tab", TAB_HISTORY);
        adapter = new DictionaryRecordsAdapter(recordStore, new DictionaryRecordsAdapter.Listener() {
            @Override
            public void onOpenHistory(DictionaryRecordStore.HistoryEntry entry) {
                openQuery(entry.query, entry.mode);
            }

            @Override
            public void onOpenFavorite(DictionaryRecordStore.FavoriteEntry entry) {
                openQuery(entry.headword, entry.mode);
            }

            @Override
            public void onRemoveHistory(DictionaryRecordStore.HistoryEntry entry) {
                recordStore.removeHistory(entry);
                refresh();
            }

            @Override
            public void onRemoveFavorite(DictionaryRecordStore.FavoriteEntry entry) {
                recordStore.removeFavorite(entry);
                refresh();
            }
        });
        binding.recordsList.setLayoutManager(new LinearLayoutManager(this));
        binding.recordsList.setAdapter(adapter);

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.records_tab_history));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.records_tab_favorites));
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                refresh();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                refresh();
            }
        });
        TabLayout.Tab tab = binding.tabLayout.getTabAt(
                selectedTab == TAB_FAVORITES ? TAB_FAVORITES : TAB_HISTORY);
        if (tab != null) tab.select();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) refresh();
    }

    private void refresh() {
        if (adapter == null) return;
        boolean historyTab = selectedTab == TAB_HISTORY;
        adapter.submit(historyTab
                ? DictionaryRecordsAdapter.ListData.history(recordStore.history())
                : DictionaryRecordsAdapter.ListData.favorites(recordStore.favorites()));

        boolean empty = adapter.getItemCount() == 0;
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recordsList.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyTitle.setText(historyTab
                ? R.string.records_history_empty_title
                : R.string.records_favorites_empty_title);
        binding.emptyText.setText(historyTab
                ? R.string.records_history_empty_text
                : R.string.records_favorites_empty_text);
    }

    private void openQuery(String query, int mode) {
        if (query == null || query.trim().isEmpty()) return;
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_QUERY, query);
        intent.putExtra(EXTRA_MODE, mode);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (selectedTab == TAB_HISTORY && !recordStore.history().isEmpty()) {
            menu.add(Menu.NONE, R.id.menu_clear_history, Menu.NONE,
                    R.string.records_clear_history)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.removeItem(R.id.menu_clear_history);
        if (selectedTab == TAB_HISTORY && !recordStore.history().isEmpty()) {
            menu.add(Menu.NONE, R.id.menu_clear_history, Menu.NONE,
                    R.string.records_clear_history)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_clear_history) {
            confirmClearHistory();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.records_clear_history)
                .setMessage(R.string.records_clear_history_confirm)
                .setPositiveButton(R.string.records_action_clear, (dialog, which) -> {
                    recordStore.clearHistory();
                    invalidateOptionsMenu();
                    refresh();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selected_tab", selectedTab);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
