package cc.ecisr.jyutdict;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.net.Uri;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import cc.ecisr.jyutdict.databinding.ActivityTaishanBinding;
import cc.ecisr.jyutdict.utils.ChineseTextConverter;
import cc.ecisr.jyutdict.utils.ImmersiveBarUtil;
import cc.ecisr.jyutdict.utils.MotionUtil;
import cc.ecisr.jyutdict.utils.ThemeUtil;
import cc.ecisr.jyutdict.utils.PronunciationPlayer;

/** Reader for user-imported dictionaries and online Taishanese sources. */
public class TaishanActivity extends LocalizedActivity {
    private ActivityTaishanBinding binding;
    private TaishanEntryAdapter adapter;
    private TaishanOnlineAdapter onlineAdapter;
    private TaishanRecordingStore recordings;
    private String pendingRecordingKey;
    private boolean importingRecording;
    private boolean importingDictionary;
    private final ActivityResultLauncher<String[]> dictionaryPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || importingDictionary) return;
                importingDictionary = true;
                Toast.makeText(this, R.string.taishan_dictionary_importing, Toast.LENGTH_SHORT).show();
                this.executor.execute(() -> {
                    boolean success;
                    try {
                        TaishanDictionary.importDictionary(getApplicationContext(), uri);
                        success = true;
                    } catch (Exception exception) {
                        success = false;
                    }
                    final boolean imported = success;
                    this.mainHandler.post(() -> {
                        if (isDestroyed() || binding == null) return;
                        importingDictionary = false;
                        Toast.makeText(this, imported ? R.string.taishan_dictionary_imported
                                : R.string.taishan_dictionary_import_failed, Toast.LENGTH_LONG).show();
                        if (imported) {
                            selectedCategory = null;
                            updateFilterButton();
                            binding.taishanModeGroup.check(R.id.taishan_mode_local);
                            loadEntries();
                        }
                    });
                });
            });
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService onlineExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, List<TaishanAudioSource.Recording>> onlineRecordings =
            new LinkedHashMap<String, List<TaishanAudioSource.Recording>>(40, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<TaishanAudioSource.Recording>> eldest) {
                    return size() > 40;
                }
            };
    private final Map<String, Integer> audioStatuses = new HashMap<>();
    private TaishanAudioRepository audioRepository;
    private Future<?> audioLookup;
    private TaishanDictionaryRepository dictionaryRepository;
    private Future<?> dictionaryLookup;
    private long dictionaryToken;
    private boolean onlineMode;
    private boolean partialMatch = true;
    private TaishanAudioSource.SearchField searchField = TaishanAudioSource.SearchField.TAISHANESE;
    private String lastOnlineQuery = "";
    private final Runnable dictionarySearchRequest = this::runOnlineSearch;
    private String lookupKey;
    private long audioToken;
    private AlertDialog audioDialog;
    private final PronunciationPlayer.Listener referenceListener = new PronunciationPlayer.Listener() {
        @Override
        public void onPlaybackChanged(String key, PronunciationPlayer.State state) {}

        @Override
        public void onReferenceFailed(String key, String url, boolean unavailable) {
            if (unavailable) {
                TaishanAudioRepository.markUnavailable(TaishanActivity.this, url);
                removeUnavailableRecording(url);
                audioStatuses.put(key, R.string.taishan_audio_no_result);
            } else {
                audioStatuses.put(key, R.string.taishan_audio_playback_failed);
            }
            if (adapter != null) adapter.refreshAudio();
        }
    };
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String[]> audioPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                String key = pendingRecordingKey;
                pendingRecordingKey = null;
                if (uri == null || key == null) return;
                importingRecording = true;
                Toast.makeText(this, R.string.taishan_audio_importing, Toast.LENGTH_SHORT).show();
                executor.execute(() -> {
                    boolean success;
                    try {
                        recordings.importRecording(key, uri);
                        success = true;
                    } catch (Exception exception) {
                        success = false;
                    }
                    final boolean imported = success;
                    mainHandler.post(() -> {
                        if (isDestroyed() || binding == null) return;
                        importingRecording = false;
                        adapter.refreshAudio();
                        Toast.makeText(this, imported ? R.string.taishan_audio_imported
                                : R.string.taishan_audio_import_failed, Toast.LENGTH_LONG).show();
                    });
                });
            });
    private volatile List<TaishanEntry> entries = Collections.emptyList();
    private final List<String> categories = new ArrayList<>();

    @Nullable
    private String selectedCategory;
    private long loadToken;
    private long filterToken;
    private volatile boolean dataReady;
    private boolean offlineLoadFailed;
    private final Runnable filterRequest = this::runFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeUtil.isNightMode(this) ? R.style.DarkTheme : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityTaishanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ImmersiveBarUtil.setImmersiveBar(this, false, !ThemeUtil.isNightMode(this));
        ImmersiveBarUtil.applyToolbarInsets(binding.toolbar);
        ImmersiveBarUtil.applyBottomInsets(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.taishan_title);
            binding.toolbar.setSubtitleTextColor(ContextCompat.getColor(
                    this, R.color.colorWhite));
        }

        recordings = new TaishanRecordingStore(this);
        onlineMode = !TaishanDictionary.isImported(this);
        if (savedInstanceState != null) {
            pendingRecordingKey = savedInstanceState.getString("recording_key");
            onlineMode = savedInstanceState.getBoolean("taishan_online_mode", onlineMode);
            partialMatch = savedInstanceState.getBoolean("taishan_partial_match", true);
            int field = savedInstanceState.getInt("taishan_search_field", 0);
            if (field >= 0 && field < TaishanAudioSource.SearchField.values().length) {
                searchField = TaishanAudioSource.SearchField.values()[field];
            }
        }
        adapter = new TaishanEntryAdapter(recordings, new TaishanEntryAdapter.AudioActions() {
            @Override
            public void play(TaishanEntry entry) {
                playEntry(entry);
            }

            @Override
            public void manage(TaishanEntry entry) {
                manageRecording(entry);
            }

            @Override
            public void reference(TaishanEntry entry) {
                cancelAudioLookup();
                PronunciationPlayer.stop();
                lookupOnline(entry, true);
            }

            @Override
            public boolean isLookingUp(TaishanEntry entry) {
                return TaishanRecordingStore.key(entry).equals(lookupKey);
            }

            @Override
            public CharSequence onlineStatus(TaishanEntry entry) {
                Integer status = audioStatuses.get(TaishanRecordingStore.key(entry));
                return getString(isLookingUp(entry) ? R.string.taishan_audio_searching
                        : status == null ? R.string.taishan_audio_online : status);
            }
        });
        PronunciationPlayer.addListener(referenceListener);
        binding.taishanList.setLayoutManager(new LinearLayoutManager(this));
        binding.taishanList.setAdapter(adapter);
        onlineAdapter = new TaishanOnlineAdapter();
        binding.taishanOnlineList.setLayoutManager(new LinearLayoutManager(this));
        binding.taishanOnlineList.setAdapter(onlineAdapter);
        binding.taishanModeGroup.check(onlineMode
                ? R.id.taishan_mode_online : R.id.taishan_mode_local);
        binding.taishanModeGroup.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (checked) setOnlineMode(checkedId == R.id.taishan_mode_online);
        });
        binding.taishanFilter.setVisibility(onlineMode ? View.GONE : View.VISIBLE);
        binding.taishanOnlineOptions.setVisibility(onlineMode ? View.VISIBLE : View.GONE);
        binding.taishanOnlineOptions.setText(searchFieldLabel(searchField));
        binding.taishanMatchMode.setVisibility(onlineMode ? View.VISIBLE : View.GONE);
        binding.taishanMatchMode.setText(partialMatch
                ? R.string.taishan_search_partial : R.string.taishan_search_full);
        binding.taishanOnlineOptions.setOnClickListener(v -> showOnlineOptions());
        binding.taishanMatchMode.setOnClickListener(v -> showMatchMode());
        binding.taishanOnlineError.setOnClickListener(v -> scheduleOnlineSearch(true));
        binding.taishanSearchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && onlineMode) {
                scheduleOnlineSearch(true);
                return true;
            }
            return false;
        });
        binding.taishanClear.setOnClickListener(v -> binding.taishanSearchInput.setText(""));
        binding.taishanFilter.setOnClickListener(v -> showCategoryPicker());
        binding.taishanError.setOnClickListener(v -> chooseDictionary());
        binding.taishanSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                binding.taishanClear.setVisibility(
                        s.length() == 0 ? View.GONE : View.VISIBLE);
                if (onlineMode) scheduleOnlineSearch(false);
                else applyFilter();
            }
        });

        if (onlineMode) {
            binding.taishanProgress.setVisibility(View.GONE);
            binding.taishanList.setVisibility(View.GONE);
        } else {
            MotionUtil.showOnly(binding.taishanStateContainer, binding.taishanProgress,
                    binding.taishanProgress, binding.taishanError, binding.taishanList,
                    binding.taishanEmpty);
        }
        loadEntries();
        if (onlineMode) {
            updateOnlineCount(null);
            scheduleOnlineSearch(false);
        }
    }

    private void loadEntries() {
        long token = ++loadToken;
        dataReady = false;
        executor.execute(() -> {
            try {
                List<TaishanEntry> loaded = TaishanDictionary.load(this);
                List<String> loadedCategories = buildCategories(loaded);
                runOnUiThread(() -> {
                    if (token != loadToken || isDestroyed() || binding == null) return;
                    entries = loaded;
                    dataReady = true;
                    offlineLoadFailed = false;
                    categories.clear();
                    categories.addAll(loadedCategories);
                    if (!onlineMode) {
                        MotionUtil.showOnly(binding.taishanStateContainer, binding.taishanList,
                                binding.taishanProgress, binding.taishanError, binding.taishanList,
                                binding.taishanEmpty);
                        applyFilter();
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (token != loadToken || isDestroyed() || binding == null) return;
                    offlineLoadFailed = true;
                    if (!onlineMode) {
                        MotionUtil.showOnly(binding.taishanStateContainer, binding.taishanError,
                                binding.taishanProgress, binding.taishanError, binding.taishanList,
                                binding.taishanEmpty);
                    }
                });
            }
        });
    }

    private void chooseDictionary() {
        if (importingDictionary) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.taishan_dictionary_import)
                .setMessage(R.string.taishan_dictionary_import_notice)
                .setPositiveButton(R.string.button_confirm, (dialog, which) ->
                        dictionaryPicker.launch(new String[]{"application/json", "text/plain", "application/octet-stream"}))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(R.string.taishan_dictionary_import).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) return super.onOptionsItemSelected(item);
        chooseDictionary();
        return true;
    }

    private void applyFilter() {
        ++filterToken;
        mainHandler.removeCallbacks(filterRequest);
        if (!dataReady) return;
        mainHandler.postDelayed(filterRequest, 120L);
    }

    private void runFilter() {
        if (!dataReady || binding == null || onlineMode || isFinishing() || isDestroyed()) return;
        long token = filterToken;
        String query = binding == null || binding.taishanSearchInput.getText() == null
                ? "" : binding.taishanSearchInput.getText().toString();
        String category = selectedCategory;
        executor.execute(() -> {
            List<TaishanEntry> result = new ArrayList<>();
            for (TaishanEntry entry : entries) {
                if (entry.matches(query, category)) result.add(entry);
            }
            runOnUiThread(() -> {
                if (token != filterToken || isDestroyed() || binding == null) return;
                adapter.submitList(result);
                updateCount(result.size());
                MotionUtil.showOnly(binding.taishanStateContainer,
                        result.isEmpty() ? binding.taishanEmpty : binding.taishanList,
                        binding.taishanProgress, binding.taishanError, binding.taishanList,
                        binding.taishanEmpty);
            });
        });
    }

    private void updateCount(int visibleCount) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(getString(
                    R.string.taishan_count_title, visibleCount, entries.size()));
        }
    }

    private void showCategoryPicker() {
        if (categories.isEmpty()) return;
        ArrayList<String> labels = new ArrayList<>();
        labels.add(getString(R.string.search_filter_all));
        int selectedIndex = 0;
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            labels.add(ChineseTextConverter.display(category));
            if (category.equals(selectedCategory)) selectedIndex = i + 1;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.taishan_filter_title)
                .setSingleChoiceItems(labels.toArray(new String[0]), selectedIndex,
                        (dialog, which) -> {
                            selectedCategory = which == 0 ? null : categories.get(which - 1);
                            dialog.dismiss();
                            updateFilterButton();
                            applyFilter();
                        })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void updateFilterButton() {
        if (selectedCategory == null) {
            binding.taishanFilter.setText(R.string.search_filter_all);
        } else {
            binding.taishanFilter.setText(ChineseTextConverter.display(shortCategory(
                    selectedCategory)));
        }
    }

    private static String shortCategory(@NonNull String category) {
        int separator = category.indexOf('、');
        return separator >= 0 && separator + 1 < category.length()
                ? category.substring(separator + 1) : category;
    }

    private static List<String> buildCategories(@NonNull List<TaishanEntry> entries) {
        LinkedHashSet<String> uniqueCategories = new LinkedHashSet<>();
        for (TaishanEntry entry : entries) {
            if (!entry.category.isEmpty()) uniqueCategories.add(entry.category);
        }
        return new ArrayList<>(uniqueCategories);
    }

    private void manageRecording(TaishanEntry entry) {
        if (importingRecording) {
            Toast.makeText(this, R.string.taishan_audio_importing, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean exists = recordings.contains(entry);
        String title = ChineseTextConverter.display(entry.headword.isEmpty()
                ? getString(R.string.taishan_missing_headword) : entry.headword) + " · " + entry.ipa;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(R.string.taishan_audio_import_notice)
                .setPositiveButton(exists ? R.string.taishan_audio_replace : R.string.taishan_audio_import,
                        (which, button) -> {
                            cancelAudioLookup();
                            PronunciationPlayer.stop();
                            pendingRecordingKey = TaishanRecordingStore.key(entry);
                            audioPicker.launch(new String[]{"audio/*"});
                        })
                .setNegativeButton(R.string.button_cancel, null);
        if (exists) {
            dialog.setNeutralButton(R.string.taishan_audio_delete, (which, button) -> {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.taishan_audio_delete)
                        .setMessage(title)
                        .setPositiveButton(R.string.button_confirm, (confirmation, selected) -> {
                            if (PronunciationPlayer.isActive(TaishanRecordingStore.key(entry))) PronunciationPlayer.stop();
                            recordings.delete(entry);
                            adapter.refreshAudio();
                        })
                        .setNegativeButton(R.string.button_cancel, null)
                        .show();
            });
        }
        dialog.show();
    }

    private void playEntry(TaishanEntry entry) {
        String key = TaishanRecordingStore.key(entry);
        boolean wasSearching = key.equals(lookupKey);
        cancelAudioLookup();
        if (wasSearching) return;
        if (PronunciationPlayer.isActive(key)) {
            PronunciationPlayer.stop();
            return;
        }
        PronunciationPlayer.stop();
        if (recordings.contains(entry)) {
            PronunciationPlayer.playLocal(this, key, recordings.file(entry));
            return;
        }
        lookupOnline(entry, false);
    }

    private void lookupOnline(TaishanEntry entry, boolean showChoices) {
        if (entry.headword.isEmpty()) return;
        String key = TaishanRecordingStore.key(entry);
        String query = TaishanAudioSource.queryWord(entry.headword);
        List<TaishanAudioSource.Recording> cached = onlineRecordings.get(query);
        if (cached != null) {
            useOnlineRecordings(entry, cached, showChoices);
            return;
        }
        long token = ++audioToken;
        lookupKey = key;
        TaishanAudioRepository repository = new TaishanAudioRepository(this);
        audioRepository = repository;
        adapter.refreshAudio();
        audioLookup = audioExecutor.submit(() -> {
            try {
                List<TaishanAudioSource.Recording> result = repository.lookup(query);
                mainHandler.post(() -> {
                    if (token != audioToken || binding == null || isFinishing() || isDestroyed()) return;
                    lookupKey = null;
                    audioRepository = null;
                    audioLookup = null;
                    if (!result.isEmpty()) onlineRecordings.put(query, result);
                    useOnlineRecordings(entry, result, showChoices);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    if (token != audioToken || binding == null || isFinishing() || isDestroyed()) return;
                    lookupKey = null;
                    audioRepository = null;
                    audioLookup = null;
                    audioStatuses.put(key, R.string.taishan_audio_lookup_failed);
                    adapter.refreshAudio();
                    Toast.makeText(this, R.string.taishan_audio_lookup_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void useOnlineRecordings(TaishanEntry entry, List<TaishanAudioSource.Recording> candidates, boolean showChoices) {
        String key = TaishanRecordingStore.key(entry);
        TaishanAudioSource.Recording exact = TaishanAudioSource.uniqueMatch(candidates, entry.rawIpa);
        audioStatuses.put(key, candidates.isEmpty() ? R.string.taishan_audio_no_result
                : exact != null ? R.string.taishan_audio_reference : R.string.taishan_audio_options);
        adapter.refreshAudio();
        if (exact != null && !showChoices) {
            PronunciationPlayer.playTaishanReference(this, key, exact.url);
        } else {
            if (audioDialog != null) audioDialog.dismiss();
            audioDialog = TaishanAudioDialog.show(this, entry, candidates);
        }
    }

    private void setOnlineMode(boolean online) {
        if (onlineMode == online || binding == null) return;
        onlineMode = online;
        cancelDictionarySearch();
        binding.taishanFilter.setVisibility(online ? View.GONE : View.VISIBLE);
        binding.taishanOnlineOptions.setVisibility(online ? View.VISIBLE : View.GONE);
        binding.taishanMatchMode.setVisibility(online ? View.VISIBLE : View.GONE);
        binding.taishanList.setVisibility(View.GONE);
        binding.taishanOnlineList.setVisibility(View.GONE);
        binding.taishanProgress.setVisibility(View.GONE);
        binding.taishanOnlineProgress.setVisibility(View.GONE);
        binding.taishanError.setVisibility(View.GONE);
        binding.taishanOnlineError.setVisibility(View.GONE);
        binding.taishanEmpty.setVisibility(View.GONE);
        binding.taishanOnlineEmpty.setVisibility(View.GONE);
        binding.taishanOnlineLimitNotice.setVisibility(View.GONE);
        binding.taishanOnlineEmpty.setText(R.string.taishan_online_empty);
        if (online) {
            lastOnlineQuery = "";
            updateOnlineCount(null);
            scheduleOnlineSearch(false);
        } else {
            if (offlineLoadFailed) {
                MotionUtil.showOnly(binding.taishanStateContainer, binding.taishanError,
                        binding.taishanProgress, binding.taishanError, binding.taishanList,
                        binding.taishanEmpty);
            } else {
                updateCount(adapter.getItemCount());
                applyFilter();
            }
        }
    }

    private void showOnlineOptions() {
        int selected = searchField.ordinal();
        ArrayAdapter<CharSequence> fields = ArrayAdapter.createFromResource(this,
                R.array.taishan_search_fields, android.R.layout.simple_list_item_single_choice);
        new AlertDialog.Builder(this)
                .setTitle(R.string.taishan_search_field_title)
                .setSingleChoiceItems(fields, selected, (dialog, which) -> {
                    searchField = TaishanAudioSource.SearchField.values()[which];
                    binding.taishanOnlineOptions.setText(searchFieldLabel(searchField));
                    dialog.dismiss();
                    scheduleOnlineSearch(true);
                })
                .setNegativeButton(R.string.button_cancel, null)
                .setNeutralButton(R.string.taishan_online_open_source, (dialog, which) ->
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(TaishanAudioSource.HOME))))
                .show();
    }

    private void showMatchMode() {
        String[] choices = {
                getString(R.string.taishan_search_partial),
                getString(R.string.taishan_search_full)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.taishan_search_match_title)
                .setSingleChoiceItems(choices, partialMatch ? 0 : 1, (dialog, which) -> {
                    partialMatch = which == 0;
                    binding.taishanMatchMode.setText(partialMatch
                            ? R.string.taishan_search_partial : R.string.taishan_search_full);
                    dialog.dismiss();
                    scheduleOnlineSearch(true);
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private int searchFieldLabel(TaishanAudioSource.SearchField field) {
        switch (field) {
            case IPA: return R.string.taishan_search_field_ipa;
            case CANTONESE: return R.string.taishan_search_field_cantonese;
            case MANDARIN: return R.string.taishan_search_field_mandarin;
            case ENGLISH: return R.string.taishan_search_field_english;
            case DESCRIPTION: return R.string.taishan_search_field_description;
            default: return R.string.taishan_search_field_taishanese;
        }
    }

    private void scheduleOnlineSearch(boolean immediate) {
        if (!onlineMode || binding == null) return;
        mainHandler.removeCallbacks(dictionarySearchRequest);
        cancelDictionarySearch();
        lastOnlineQuery = "";
        if (!immediate && !binding.taishanSearchInput.getText().toString().trim().isEmpty()) {
            mainHandler.postDelayed(dictionarySearchRequest, 450L);
        } else {
            mainHandler.post(dictionarySearchRequest);
        }
    }

    private void runOnlineSearch() {
        if (!onlineMode || binding == null || isFinishing() || isDestroyed()) return;
        String query = binding.taishanSearchInput.getText() == null
                ? "" : binding.taishanSearchInput.getText().toString().trim();
        if (query.isEmpty()) {
            cancelDictionarySearch();
            lastOnlineQuery = "";
            onlineAdapter.submit(Collections.emptyList());
            binding.taishanOnlineList.setVisibility(View.GONE);
            binding.taishanOnlineError.setVisibility(View.GONE);
            binding.taishanOnlineProgress.setVisibility(View.GONE);
            binding.taishanOnlineLimitNotice.setVisibility(View.GONE);
            binding.taishanOnlineEmpty.setText(R.string.taishan_online_empty);
            binding.taishanOnlineEmpty.setVisibility(View.VISIBLE);
            updateOnlineCount(null);
            return;
        }
        String searchKey = query + "\u0000" + searchField.name() + "\u0000" + partialMatch;
        if (searchKey.equals(lastOnlineQuery) && dictionaryLookup == null) return;
        lastOnlineQuery = searchKey;
        cancelDictionarySearch();
        long token = ++dictionaryToken;
        TaishanDictionaryRepository repository = new TaishanDictionaryRepository();
        dictionaryRepository = repository;
        binding.taishanOnlineProgress.setVisibility(View.VISIBLE);
        binding.taishanOnlineError.setVisibility(View.GONE);
        binding.taishanOnlineEmpty.setVisibility(View.GONE);
        dictionaryLookup = onlineExecutor.submit(() -> {
            try {
                TaishanAudioSource.DictionaryResult result = repository.search(
                        query, searchField, partialMatch);
                mainHandler.post(() -> {
                    if (token != dictionaryToken || binding == null || !onlineMode
                            || isFinishing() || isDestroyed()) return;
                    dictionaryLookup = null;
                    dictionaryRepository = null;
                    binding.taishanOnlineProgress.setVisibility(View.GONE);
                    onlineAdapter.submit(result.entries);
                    binding.taishanOnlineLimitNotice.setVisibility(result.truncated ? View.VISIBLE : View.GONE);
                    if (result.truncated) {
                        binding.taishanOnlineLimitNotice.setText(getString(
                                R.string.taishan_online_count_limited, result.matchCount));
                    }
                    int listTopPadding = Math.round((result.truncated ? 48 : 4)
                            * getResources().getDisplayMetrics().density);
                    binding.taishanOnlineList.setPadding(
                            binding.taishanOnlineList.getPaddingLeft(), listTopPadding,
                            binding.taishanOnlineList.getPaddingRight(),
                            binding.taishanOnlineList.getPaddingBottom());
                    binding.taishanOnlineList.setVisibility(result.entries.isEmpty()
                            ? View.GONE : View.VISIBLE);
                    binding.taishanOnlineEmpty.setText(R.string.taishan_online_no_results);
                    binding.taishanOnlineEmpty.setVisibility(result.entries.isEmpty()
                            ? View.VISIBLE : View.GONE);
                    updateOnlineCount(result);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    if (token != dictionaryToken || binding == null || !onlineMode
                            || isFinishing() || isDestroyed()) return;
                    dictionaryLookup = null;
                    dictionaryRepository = null;
                    binding.taishanOnlineProgress.setVisibility(View.GONE);
                    binding.taishanOnlineLimitNotice.setVisibility(View.GONE);
                    binding.taishanOnlineList.setVisibility(View.GONE);
                    binding.taishanOnlineEmpty.setVisibility(View.GONE);
                    binding.taishanOnlineError.setVisibility(View.VISIBLE);
                    lastOnlineQuery = "";
                    updateOnlineCount(null);
                });
            }
        });
    }

    private void updateOnlineCount(@Nullable TaishanAudioSource.DictionaryResult result) {
        if (getSupportActionBar() == null) return;
        if (result == null) {
            getSupportActionBar().setSubtitle(R.string.taishan_mode_online);
        } else if (result.truncated) {
            getSupportActionBar().setSubtitle(getString(
                    R.string.taishan_online_count_limited, result.matchCount));
        } else {
            getSupportActionBar().setSubtitle(getString(
                    R.string.taishan_online_count, result.matchCount));
        }
    }

    private void cancelDictionarySearch() {
        ++dictionaryToken;
        if (dictionaryRepository != null) dictionaryRepository.cancel();
        if (dictionaryLookup != null) dictionaryLookup.cancel(true);
        dictionaryRepository = null;
        dictionaryLookup = null;
    }

    private void removeUnavailableRecording(String url) {
        for (Map.Entry<String, List<TaishanAudioSource.Recording>> item : onlineRecordings.entrySet()) {
            List<TaishanAudioSource.Recording> current = item.getValue();
            List<TaishanAudioSource.Recording> remaining = new ArrayList<>(current.size());
            for (TaishanAudioSource.Recording recording : current) {
                if (!recording.url.equals(url)) remaining.add(recording);
            }
            if (remaining.size() != current.size()) {
                item.setValue(Collections.unmodifiableList(remaining));
            }
        }
    }

    private void cancelAudioLookup() {
        ++audioToken;
        if (audioRepository != null) audioRepository.cancel();
        if (audioLookup != null) audioLookup.cancel(true);
        audioRepository = null;
        audioLookup = null;
        lookupKey = null;
        if (adapter != null) adapter.refreshAudio();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("recording_key", pendingRecordingKey);
        outState.putBoolean("taishan_online_mode", onlineMode);
        outState.putBoolean("taishan_partial_match", partialMatch);
        outState.putInt("taishan_search_field", searchField.ordinal());
    }

    @Override
    protected void onStop() {
        cancelAudioLookup();
        if (onlineMode) {
            mainHandler.removeCallbacks(dictionarySearchRequest);
            cancelDictionarySearch();
            lastOnlineQuery = "";
        }
        if (audioDialog != null) audioDialog.dismiss();
        PronunciationPlayer.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (onlineMode && binding != null
                && binding.taishanSearchInput.getText() != null
                && binding.taishanSearchInput.getText().length() > 0) {
            scheduleOnlineSearch(false);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        ++loadToken;
        ++filterToken;
        cancelAudioLookup();
        cancelDictionarySearch();
        if (audioDialog != null) audioDialog.dismiss();
        audioDialog = null;
        mainHandler.removeCallbacks(filterRequest);
        executor.shutdownNow();
        audioExecutor.shutdownNow();
        onlineExecutor.shutdownNow();
        mainHandler.removeCallbacks(dictionarySearchRequest);
        mainHandler.removeCallbacksAndMessages(null);
        if (adapter != null) adapter.dispose();
        if (onlineAdapter != null) onlineAdapter.dispose();
        PronunciationPlayer.removeListener(referenceListener);
        if (binding != null) binding.taishanList.setAdapter(null);
        if (binding != null) binding = null;
        super.onDestroy();
    }
}
