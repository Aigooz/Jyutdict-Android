package cc.ecisr.jyutdict;

import static cc.ecisr.jyutdict.utils.EnumConst.*;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.ecisr.jyutdict.databinding.FragmentResultBinding;
import cc.ecisr.jyutdict.pronunciation.PronunciationResultParser;
import cc.ecisr.jyutdict.result.ResultViewState;
import cc.ecisr.jyutdict.search.DictionaryRecordStore;
import cc.ecisr.jyutdict.struct.LocationInfo;
import android.text.SpannableStringBuilder;

import cc.ecisr.jyutdict.struct.FjbCharacter;
import cc.ecisr.jyutdict.struct.FjbHeaderInfo;
import cc.ecisr.jyutdict.struct.EntrySetting;
import cc.ecisr.jyutdict.struct.GeneralCharacterManager;
import cc.ecisr.jyutdict.utils.ColorUtil;
import cc.ecisr.jyutdict.utils.ChineseTextConverter;
import cc.ecisr.jyutdict.utils.DiskTextCache;
import cc.ecisr.jyutdict.utils.HttpUtil;
import cc.ecisr.jyutdict.utils.ImmersiveBarUtil;
import cc.ecisr.jyutdict.utils.JyutpingAudioCatalog;
import cc.ecisr.jyutdict.utils.PronunciationPlayer;
import cc.ecisr.jyutdict.utils.PronunciationOption;
import cc.ecisr.jyutdict.utils.MotionUtil;
import cc.ecisr.jyutdict.utils.ThemeUtil;
import cc.ecisr.jyutdict.widget.LocationClickSpan;
import cc.ecisr.jyutdict.widget.LocationLabelSpan;

public class ResultFragment extends Fragment {
    public interface ResultCountListener {
        void onResultCount(int count);
    }

    private static final String TAG = "ResultFragment";
    private static final String STATE_RAW = "received_data";
    private static final String STATE_RAW_CACHE_KEY = "received_data_cache_key";
    private static final String STATE_MODE = "received_mode";
    private static final String RESULT_STATE_CACHE = "result-page";
    /** Payloads above this size go to disk instead of the saved-state Bundle. */
    private static final int INLINE_STATE_LIMIT = 200_000;

    private RecyclerView mRvMain;
    private FragmentResultBinding binding;
    private LinearLayout emptyState;
    private TextView emptyStateTitle;
    private TextView emptyStateText;
    private Button emptyStateAction;
    private ResultItemAdapter resultAdapter;
    private boolean resultRevealRunning;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService parseExecutor = Executors.newSingleThreadExecutor();
    private long parseToken;

    private String rawReceivedData;
    int receivedMode = QUERYING_CHARA;
    private DictionaryRecordStore recordStore;
    @Nullable private ResultCountListener resultCountListener;
    public static HashSet<String> pronCityFilter = new HashSet<>();
    public static HashSet<String> pronBookFilter = new HashSet<>();

    // TODO 不 parse JSON in Fragment

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentResultBinding.inflate(inflater, container, false);
        mRvMain = binding.resultList;
        emptyState = binding.emptyState;
        emptyStateTitle = binding.emptyStateTitle;
        emptyStateText = binding.emptyStateText;
        emptyStateAction = binding.emptyStateAction;
        emptyStateAction.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).retrySearch();
            }
        });
        ImmersiveBarUtil.applyBottomInsets(mRvMain);
        resultAdapter = new ResultItemAdapter();
        mRvMain.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRvMain.setItemAnimator(null);
        mRvMain.setAdapter(resultAdapter);
        if (savedInstanceState!= null) {
            receivedMode = savedInstanceState.getInt(STATE_MODE);
            rawReceivedData = restoreSavedResult(savedInstanceState);
        }
        if (rawReceivedData != null && !rawReceivedData.isEmpty()) refreshResult();
        else applyResultViewState(ResultViewState.initial());
        return binding.getRoot();
    }

    @Nullable
    private String restoreSavedResult(Bundle savedInstanceState) {
        String cacheKey = savedInstanceState.getString(STATE_RAW_CACHE_KEY);
        Context context = getContext();
        String cached = cacheKey == null || context == null ? null
                : DiskTextCache.readAny(context, cacheKey);
        if (cached != null && !cached.isEmpty()) return cached;
        return savedInstanceState.getString(STATE_RAW);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Log.d(TAG, "onSaveInstanceState: " + System.identityHashCode(this));
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_MODE, receivedMode);
        if (rawReceivedData == null || rawReceivedData.isEmpty()) return;
        if (rawReceivedData.length() <= INLINE_STATE_LIMIT) {
            outState.putString(STATE_RAW, rawReceivedData);
            return;
        }
        // Wide sheet queries exceed the Binder transaction budget; keep them off the Bundle.
        Context context = getContext();
        if (context != null && DiskTextCache.write(context, RESULT_STATE_CACHE, rawReceivedData)) {
            outState.putString(STATE_RAW_CACHE_KEY, RESULT_STATE_CACHE);
        } else {
            outState.putString(STATE_RAW, rawReceivedData);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated: " + System.identityHashCode(this));
        super.onViewCreated(view, savedInstanceState);
    }

    public void refreshResult() {
        if (rawReceivedData==null || rawReceivedData.isEmpty()) return;
        try {
            parseJson(rawReceivedData, receivedMode, false);
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to schedule saved result parsing", exception);
        }
    }

    public void setResultCountListener(@Nullable ResultCountListener listener) {
        resultCountListener = listener;
    }

    public void configureRecords(DictionaryRecordStore store, int mode) {
        recordStore = store;
        if (resultAdapter != null) resultAdapter.configureRecords(store, mode);
    }
    public void refreshResult(int receivedModeConfig) {
        receivedMode = (receivedMode&QUERYING_MODE_MASK) | receivedModeConfig;
        refreshResult();
    }

    public void beginLoading() {
        if (mRvMain == null) return;
        ResultViewState state = ResultViewState.loading();
        hideEmptyState();
        if (state.listDimmed) mRvMain.setAlpha(0.55f);
        resultRevealRunning = false;
        if (state.listDimmed) MotionUtil.fadeTo(mRvMain, 0.55f);
    }

    public void finishLoading() {
        if (mRvMain == null) return;
        if (resultRevealRunning) {
            resultRevealRunning = false;
            return;
        }
        mRvMain.setTranslationY(0f);
        MotionUtil.fadeTo(mRvMain, 1f);
    }

    @Override
    public void onDestroyView() {
        ++parseToken;
        if (mRvMain != null) {
            mRvMain.animate().cancel();
            mRvMain.setAdapter(null);
        }
        if (resultAdapter != null) resultAdapter.dispose();
        resultAdapter = null;
        mRvMain = null;
        emptyState = null;
        emptyStateTitle = null;
        emptyStateText = null;
        emptyStateAction = null;
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        ++parseToken;
        parseExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        Activity host = getActivity();
        if (host != null && host.isFinishing()) GeneralCharacterManager.clearLastParsed();
        super.onDestroy();
    }

    /**
     * 將服務器返回的 JSON 字符串處理成可閱讀的樣式，並顯示出來
     * 在 {@code MainActivity} 成功收到查詢回應（JSON 字符串）時調用
     * 在本方法解析 JSON 字符串生成每個條目的五個 layout 的 spanned，再交由 {@code ResultItemAdapter} 顯示
     * 查詢通用字表的兩個模式（查字&查音）用的是內嵌的解析，且生成的是 HTML 格式。應棄用
     * TODO 好像會發生內存洩露？
     *
     * @param jsonString 服務器返回的 JSON 字符串
     * @param queryObjectWhat 查詢模式(通用表查字/查音/查泛粵表 等)，值在 {@code EnumConst} 類中定義
     * @see cc.ecisr.jyutdict.utils.EnumConst
     */
    void parseJson(String jsonString, int queryObjectWhat) throws JSONException {
        parseJson(jsonString, queryObjectWhat, true);
    }

    private void parseJson(String jsonString, int queryObjectWhat, boolean revealNewResult)
            throws JSONException {
        if (jsonString == null || jsonString.isEmpty() || resultAdapter == null) return;
        Context context = getContext();
        if (context == null) return;
        PronunciationPlayer.stop();
        final long token = ++parseToken;
        rawReceivedData = jsonString;
        receivedMode = queryObjectWhat;
        if (resultAdapter != null) resultAdapter.configureRecords(recordStore, queryObjectWhat);
        parseExecutor.execute(() -> {
            try {
                ArrayList<ResultItemAdapter.ResultInfo> parsedItems =
                        parseJsonValidated(jsonString, queryObjectWhat,
                                context.getApplicationContext());
                mainHandler.post(() -> {
                    if (token != parseToken || !isAdded() || resultAdapter == null
                            || getView() == null) return;
                    resultAdapter.replaceItems(parsedItems);
                    publishResultViews(revealNewResult);
                    if (resultCountListener != null) {
                        resultCountListener.onResultCount(parsedItems.size());
                    }
                });
            } catch (JSONException | RuntimeException exception) {
                Log.e(TAG, "Unable to parse result", exception);
                mainHandler.post(() -> {
                    if (token == parseToken && isAdded() && emptyState != null) {
                        showParseError();
                    }
                });
            }
        });
    }

    public void showParseError() {
        if (emptyState == null) return;
        applyResultViewState(ResultViewState.parseError());
    }

    public void showNetworkError() {
        if (emptyState == null) return;
        applyResultViewState(ResultViewState.networkError());
    }

    public void showRequestError(@Nullable HttpUtil.RequestError error) {
        if (emptyState == null) return;
        applyResultViewState(ResultViewState.requestError(error));
    }

    /** Clears stale rows when local validation prevents a new request from being sent. */
    public void showInvalidRequest() {
        if (emptyState == null) return;
        rawReceivedData = null;
        Context context = getContext();
        if (context != null) DiskTextCache.remove(context.getApplicationContext(),
                RESULT_STATE_CACHE);
        cancelPendingParse();
        applyResultViewState(ResultViewState.invalidRequestError());
        if (resultCountListener != null) resultCountListener.onResultCount(0);
    }

    private void cancelPendingParse() {
        ++parseToken;
        if (resultAdapter != null) resultAdapter.clearItems();
    }

    private ArrayList<ResultItemAdapter.ResultInfo> parseJsonValidated(
            String jsonString, int queryObjectWhat, Context context) throws JSONException {

        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        ArrayList<ResultItemAdapter.ResultInfo> parsedItems = new ArrayList<>();
        EntrySetting entrySettings = new EntrySetting()
                .setAreaColoringInfo(
                        sp.getBoolean("area_coloring", true),
                        sp.getFloat("area_coloring_darken_ratio", 0.92f))
                .setMeaningDomainPresence(
                        sp.getBoolean("phrase_meaning_domain", false))
                .setUsingNightMode(ThemeUtil.isNightMode(context))
                .setPresentIpa(sp.getBoolean("ipa_presence", true));
        switch (queryObjectWhat & QUERYING_MODE_MASK) {
            case QUERYING_CHARA:
                GeneralCharacterManager gcm = new GeneralCharacterManager();
                gcm.parse(jsonString, entrySettings); /// 為什麼要重新解析一次原始字符串？真傻
                gcm.retrieveInfo();
                GeneralCharacterManager.initAudioCatalog(context);
                gcm.coloring(queryObjectWhat & DISPLAY_CHECKING_MASK);
                for (int i = 0; i<gcm.length(); i++) {
                    Spanned[] spanneds = gcm.printChara(i);
                    ResultItemAdapter.ResultInfo item = addItem(parsedItems,
                            spanneds[0], spanneds[1], spanneds[2],
                            spanneds[3], spanneds[4],
                            ResultItemAdapter.ResultInfo.TYPE_GENERAL);
                    if (item != null) {
                        item.pronunciations = gcm.pronunciationOptions(i);
                    }
                }
                break;
            case QUERYING_PRON:
                parseJsonPron(jsonString, entrySettings, context, parsedItems);
                break;
            case QUERYING_SHEET:
                JyutpingAudioCatalog audioCatalog = JyutpingAudioCatalog.read(context);
                FjbCharacter character; // TODO: Use ManagerClass like QUERYING_CHARA.
                JSONArray jsonArray = new JSONArray(jsonString);
                ArrayList<JSONObject> sortedEntries = new ArrayList<>(jsonArray.length());
                for (int i = 0; i < jsonArray.length(); i++) {
                    sortedEntries.add(jsonArray.getJSONObject(i));
                }
                // 與網頁端一致：地方讀音覆蓋度越高的條目越靠前；TimSort 保持同分原序。
                Collections.sort(sortedEntries, (left, right) -> Integer.compare(
                        FjbHeaderInfo.densityScore(right),
                        FjbHeaderInfo.densityScore(left)
                ));
                for (JSONObject entry : sortedEntries) {
                    character = new FjbCharacter(entry, entrySettings, audioCatalog);

                    ResultItemAdapter.ResultInfo item = addItem(parsedItems,
                            character.printCharacter(),
                            character.printUnicode(),
                            character.printPronunciation(),
                            character.printMeanings(),
                            character.printLocations(),
                            ResultItemAdapter.ResultInfo.TYPE_SHEET
                    );
                    if (item != null) {
                        item.pronunciations = character.pronunciationOptions();
                    }
                }
                break;
            default:
                break;
        }
        return parsedItems;
    }

    /**
     * Selectable TextView 的 Editor/ActionMode 狀態會跟隨 ViewHolder 留在回收池中。
     * 同模式再次查詢若只 notifyDataSetChanged，重綁後的文字可能再也無法長按選取；
     * 發佈一批新結果時丟棄舊 holder，確保每次查詢都使用全新的選取狀態。
     */
    @SuppressLint("NotifyDataSetChanged")
    private void publishResultViews(boolean revealNewResult) {
        if (mRvMain == null || resultAdapter == null) return;
        mRvMain.stopScroll();
        if (mRvMain.getItemAnimator() != null) {
            mRvMain.getItemAnimator().endAnimations();
        }
        resultAdapter.notifyDataSetChanged();
        ResultViewState state = ResultViewState.fromResultCount(resultAdapter.getItemCount());
        applyResultViewState(state);
        boolean hasResults = state.page == ResultViewState.Page.CONTENT;
        if (!revealNewResult) {
            resultRevealRunning = false;
            mRvMain.animate().cancel();
            mRvMain.setAlpha(1f);
            mRvMain.setTranslationY(0f);
            return;
        }
        if (hasResults) {
            mRvMain.scrollToPosition(0);
        }
        resultRevealRunning = true;
        MotionUtil.reveal(mRvMain);
    }

    private void showEmptyState(int titleRes, int textRes, boolean showRetry) {
        if (emptyState == null || emptyStateTitle == null
                || emptyStateText == null || emptyStateAction == null) return;
        emptyStateTitle.setText(titleRes);
        emptyStateText.setText(textRes);
        emptyStateAction.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void hideEmptyState() {
        if (emptyState != null) emptyState.setVisibility(View.GONE);
    }

    private void applyResultViewState(ResultViewState state) {
        if (state.clearResults && resultAdapter != null) {
            resultAdapter.clearItems();
            if (mRvMain != null) {
                mRvMain.stopScroll();
                mRvMain.scrollToPosition(0);
            }
        }
        switch (state.page) {
            case INITIAL:
                showEmptyState(R.string.result_initial_title,
                        R.string.result_initial_description, state.retryVisible);
                break;
            case EMPTY:
                showEmptyState(R.string.result_empty_title,
                        R.string.result_empty_description, state.retryVisible);
                break;
            case PARSE_ERROR:
                showEmptyState(R.string.result_error_title,
                        R.string.result_error_data, state.retryVisible);
                break;
            case REQUEST_ERROR:
                showEmptyState(R.string.result_error_title,
                        R.string.result_error_request, state.retryVisible);
                break;
            case VALIDATION_ERROR:
                showEmptyState(R.string.result_validation_title,
                        R.string.result_validation_description, state.retryVisible);
                break;
            case NETWORK_ERROR:
                showEmptyState(R.string.result_error_title,
                        R.string.result_error_network, state.retryVisible);
                break;
            case SERVER_ERROR:
                showEmptyState(R.string.result_error_title,
                        R.string.result_error_server, state.retryVisible);
                break;
            case CONTENT:
                hideEmptyState();
                break;
            case LOADING:
                hideEmptyState();
                break;
            default:
                throw new IllegalStateException("Unknown result page: " + state.page);
        }
    }

    /**
     * 向 ResultItemAdapter 添加一項條文
     *
     * @param chara layout 中的左上部分
     *              僅用於顯示字頭
     * @param leftMiddle layout 中的左中部分
     *                   顯示廣韻音（通語表）或統一碼（泛粵表）
     * @param leftBottom layout 中的左下部分
     *                   顯示綜合音
     * @param rightTop layout 中的右上部分
     *                 顯示韻書（通用表）或釋義（泛粵表）
     * @param rightBottom layout 中的右下部分
     *                    顯示地方音
     */
    private ResultItemAdapter.ResultInfo addItem(
            ArrayList<ResultItemAdapter.ResultInfo> items,
            Spanned chara, Spanned leftMiddle, Spanned leftBottom,
            Spanned rightTop, Spanned rightBottom, int type) {
        if (rightTop.length()!=0 || rightBottom.length()!=0) {
            ResultItemAdapter.ResultInfo item = new ResultItemAdapter.ResultInfo(
                    chara,
                    leftMiddle,
                    leftBottom,
                    rightTop,
                    rightBottom,
                    type
            );
            items.add(item);
            return item;
        }
        return null;
    }

    private void parseJsonPron(String jsonString, EntrySetting entrySettings, Context context,
                               ArrayList<ResultItemAdapter.ResultInfo> parsedItems)
            throws JSONException {
        List<PronunciationResultParser.Entry> entries = PronunciationResultParser.parse(
                jsonString,
                ChineseTextConverter::display,
                locationId -> {
                    LocationInfo.Location location = LocationInfo.get(locationId);
                    return location == null ? "id=" + locationId : location.displayName();
                },
                (leftId, rightId) -> LocationInfo.compareGeographically(
                        LocationInfo.get(leftId), LocationInfo.get(rightId)),
                pronBookFilter,
                pronCityFilter);
        JyutpingAudioCatalog audioCatalog = JyutpingAudioCatalog.read(
                context.getApplicationContext());
        for (PronunciationResultParser.Entry entry : entries) {
            SpannableStringBuilder label = entry.kind
                    == PronunciationResultParser.Entry.Kind.BOOK
                    ? new SpannableStringBuilder(entry.label)
                    : locationLabel(LocationInfo.get(entry.locationId),
                            entry.locationId, entrySettings);
            addPronunciationItem(parsedItems, label,
                    new SpannableStringBuilder(entry.pronunciations),
                    entry.readings, audioCatalog);
        }
    }

    private SpannableStringBuilder locationLabel(LocationInfo.Location location, int id,
                                                  EntrySetting settings) {
        String name = location == null ? "id=" + id : location.displayName();
        SpannableStringBuilder result = new SpannableStringBuilder(name);
        if (location == null || name.isEmpty()) return result;
        double ratio = settings.isUsingNightMode()
                ? 2 - settings.getAreaColoringDarkenRatio()
                : settings.getAreaColoringDarkenRatio();
        int[] colors = settings.isAreaColoring()
                ? ColorUtil.locationColorInts(location.colors, ratio) : new int[0];
        result.setSpan(new LocationLabelSpan(name, colors), 0, result.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.setSpan(new LocationClickSpan(location.id), 0, result.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }

    private void addPronunciationItem(ArrayList<ResultItemAdapter.ResultInfo> items,
                                      Spanned label, Spanned pronunciations,
                                      List<String> readings, JyutpingAudioCatalog audioCatalog) {
        if (pronunciations.length() == 0) return;
        ResultItemAdapter.ResultInfo item = addItem(
                items, new SpannableStringBuilder(), new SpannableStringBuilder(),
                new SpannableStringBuilder(), label, pronunciations,
                ResultItemAdapter.ResultInfo.TYPE_GENERAL);
        if (item == null) return;
        ArrayList<PronunciationOption> options = new ArrayList<>();
        for (String reading : readings) {
            if (audioCatalog.resolveSequence(reading).isEmpty()) continue;
            PronunciationOption option = new PronunciationOption("", reading);
            if (!options.contains(option)) options.add(option);
        }
        item.pronunciations = options;
    }

}
