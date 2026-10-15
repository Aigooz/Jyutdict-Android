package cc.ecisr.jyutdict.struct;

import static cc.ecisr.jyutdict.utils.EnumConst.*;

import android.content.res.Resources;
import android.graphics.Color;
import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import cc.ecisr.jyutdict.utils.ColorUtil;
import cc.ecisr.jyutdict.utils.ChineseTextConverter;
import cc.ecisr.jyutdict.utils.JyutpingAudioCatalog;
import cc.ecisr.jyutdict.utils.PronunciationOption;
import cc.ecisr.jyutdict.widget.FixedWidthSpan;
import cc.ecisr.jyutdict.widget.BookLabelSpan;
import cc.ecisr.jyutdict.widget.LocationClickSpan;
import cc.ecisr.jyutdict.widget.LocationLabelSpan;
import cc.ecisr.jyutdict.widget.NoBreakCandidateSpan;
import cc.ecisr.jyutdict.widget.PronunciationClickSpan;

public class GeneralCharacterManager {
    public static final String FILTER_BOOK_FANWAN = "韻書 · 分韻";
    public static final String FILTER_BOOK_JINGWAA = "韻書 · 英華";
    ArrayList<GeneralCharacter> charas = new ArrayList<>();
    HashSet<String> characterHeads = new HashSet<>();
    private final ArrayList<String> resultCities = new ArrayList<>();
    public static ArrayList<String> cityList = new ArrayList<>();
    public static HashSet<String> cityFilter = new HashSet<>(); // static 是因為 MainActivity 要調用
    public static HashSet<String> hiddenInitials = new HashSet<>();
    public static HashSet<String> hiddenFinals = new HashSet<>();
    public static HashSet<String> hiddenTones = new HashSet<>();
    EntrySetting settings;
    int colorCount = 0;
    private static JyutpingAudioCatalog audioCatalog;
    private static GeneralCharacterManager lastParsedInstance;

    public static void initAudioCatalog(Context context) {
        if (audioCatalog == null) {
            audioCatalog = JyutpingAudioCatalog.read(context.getApplicationContext());
        }
    }

    private static boolean isPlayable(String syllable) {
        if (audioCatalog == null) return false;
        return !audioCatalog.resolveSequence(syllable).isEmpty();
    }

    public List<PronunciationOption> pronunciationOptions(int index) {
        ArrayList<PronunciationOption> result = new ArrayList<>();
        for (GeneralCharacter.SingleLoc location : charas.get(index).areas) {
            if (cityFilter.contains(location.city)) continue;
            for (ArrayList<GeneralCharacter.SingleLoc.SinglePron> group : location.prons) {
                for (GeneralCharacter.SingleLoc.SinglePron pron : group) {
                    if (!isPronHidden(pron) && isPlayable(pron.syllable())) {
                        PronunciationOption option = new PronunciationOption(
                                location.city.replace("'", ""), pron.syllable());
                        if (!result.contains(option)) result.add(option);
                    }
                }
            }
        }
        return result;
    }

    private static int pronunciationColor(boolean playable, boolean nightMode) {
        if (playable) {
            return nightMode ? 0xFF66BB6A : 0xFF2E7D32;
        }
        return nightMode ? 0xFFEF5350 : 0xFFC62828;
    }

    private static boolean isPronHidden(GeneralCharacter.SingleLoc.SinglePron pron) {
        return hiddenInitials.contains(pron.jpp[0])
                || hiddenFinals.contains(pron.jpp[1])
                || hiddenTones.contains(pron.jpp[2]);
    }

    /** Collects unique initials, finals, and tones from the current search results. */
    public static void collectAvailableProns(
            GeneralCharacterManager manager,
            ArrayList<String> initials,
            ArrayList<String> finals,
            ArrayList<String> tones) {
        HashSet<String> iniSet = new HashSet<>();
        HashSet<String> finSet = new HashSet<>();
        HashSet<String> tonSet = new HashSet<>();
        for (GeneralCharacter chara : manager.charas) {
            for (GeneralCharacter.SingleLoc loc : chara.areas) {
                for (ArrayList<GeneralCharacter.SingleLoc.SinglePron> group : loc.prons) {
                    for (GeneralCharacter.SingleLoc.SinglePron pron : group) {
                        if (!pron.jpp[0].isEmpty()) iniSet.add(pron.jpp[0]);
                        if (!pron.jpp[1].isEmpty()) finSet.add(pron.jpp[1]);
                        if (!pron.jpp[2].isEmpty()) tonSet.add(pron.jpp[2]);
                    }
                }
            }
        }
        initials.addAll(iniSet);
        finals.addAll(finSet);
        tones.addAll(tonSet);
        java.util.Collections.sort(initials);
        java.util.Collections.sort(finals);
        java.util.Collections.sort(tones);
    }

    public void parse(String raw, EntrySetting settings) throws JSONException {
        JSONArray charasJSON = new JSONArray(raw);
        for (int i=0; i<charasJSON.length(); i++) {
            JSONObject chara = charasJSON.optJSONObject(i);
            if (chara == null) continue;
            if (!characterHeads.add(chara.optString("字"))) continue;
            charas.add(new GeneralCharacter(chara));
        }
        this.settings = settings;
        lastParsedInstance = this;
    }

    public static GeneralCharacterManager getLastParsed() {
        return lastParsedInstance;
    }

    public void retrieveInfo() {
        resultCities.clear();
        for (GeneralCharacter character : charas) {
            for (GeneralCharacter.SingleLoc location : character.areas) {
                if (!resultCities.contains(location.city)) resultCities.add(location.city);
            }
        }
    }

    public void coloring(int displayMode) {
        boolean ini = (displayMode & DISPLAY_CHECKING_INI) != 0;
        boolean fin = (displayMode & DISPLAY_CHECKING_FIN) != 0;
        boolean ton = (displayMode & DISPLAY_CHECKING_TON) != 0;
        colorCount = 0;
        if (!ini && !fin && !ton) return;
        boolean inner = (displayMode & DISPLAY_CHECKING_IS_INNER) != 0;
        if (inner) {
            for (GeneralCharacter character : charas) {
                ArrayList<ArrayList<ArrayList<GeneralCharacter.SingleLoc.SinglePron>>> groups =
                        new ArrayList<>();
                for (GeneralCharacter.SingleLoc location : character.areas) {
                    if (!cityFilter.contains(location.city)) groups.add(location.prons);
                }
                colorCount = Math.max(colorCount, colorMatches(groups, ini, fin, ton));
            }
        } else {
            for (String city : resultCities) {
                if (cityFilter.contains(city)) continue;
                ArrayList<ArrayList<ArrayList<GeneralCharacter.SingleLoc.SinglePron>>> groups =
                        new ArrayList<>();
                for (GeneralCharacter character : charas) {
                    ArrayList<ArrayList<GeneralCharacter.SingleLoc.SinglePron>> cityProns =
                            new ArrayList<>();
                    for (GeneralCharacter.SingleLoc location : character.areas) {
                        if (city.equals(location.city)) cityProns.addAll(location.prons);
                    }
                    groups.add(cityProns);
                }
                colorCount = Math.max(colorCount, colorMatches(groups, ini, fin, ton));
            }
        }
    }

    private int colorMatches(
            ArrayList<ArrayList<ArrayList<GeneralCharacter.SingleLoc.SinglePron>>> groups,
            boolean initial, boolean fin, boolean tone) {
        int nextColor = 0;
        HashMap<String, Integer> colors = new HashMap<>();
        for (int left = 0; left < groups.size() - 1; left++) {
            for (ArrayList<GeneralCharacter.SingleLoc.SinglePron> alternatives : groups.get(left)) {
                for (GeneralCharacter.SingleLoc.SinglePron pronunciation : alternatives) {
                    String key = pronunciation.jpp(initial, fin, tone);
                    for (int right = left + 1; right < groups.size(); right++) {
                        for (ArrayList<GeneralCharacter.SingleLoc.SinglePron> rightAlternatives
                                : groups.get(right)) {
                            for (GeneralCharacter.SingleLoc.SinglePron candidate : rightAlternatives) {
                                if (!key.equals(candidate.jpp(initial, fin, tone))) continue;
                                if (!colors.containsKey(key)) colors.put(key, ++nextColor);
                                pronunciation.coloring = candidate.coloring = colors.get(key);
                            }
                        }
                    }
                }
            }
        }
        return nextColor;
    }


    public Spanned[] printChara(int index) {
        if (index >= charas.size()) return new Spanned[5];
        GeneralCharacter chara = charas.get(index);
        String charaHead = chara.head;

        SpannableStringBuilder contentCharaInfo = new SpannableStringBuilder("");
        for (int i = 0; i < chara.books.kwangun.size(); i++) {
            if (i!=0) contentCharaInfo.append(" · ");
            contentCharaInfo.append(ChineseTextConverter.display(chara.books.kwangun.get(i)));
        }
        if (contentCharaInfo.length() > 0) {
            contentCharaInfo.setSpan(new RelativeSizeSpan(0.8f),
                    0, contentCharaInfo.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        SpannableStringBuilder contentWanshyu = new SpannableStringBuilder("");
        if (!isBookFiltered(FILTER_BOOK_FANWAN) && !chara.books.fanwan.isEmpty()) {
            appendBook(contentWanshyu, "分韻", chara.books.fanwan);
        }
        if (!isBookFiltered(FILTER_BOOK_JINGWAA) && !chara.books.jingwaa.isEmpty()) {
            if (contentWanshyu.length() > 0) contentWanshyu.append("\n");
            appendBook(contentWanshyu, "英華", chara.books.jingwaa);
        }

        SpannableStringBuilder contentLoc = new SpannableStringBuilder();
        ArrayList<Integer> locationParagraphStarts = new ArrayList<>();
        ArrayList<Integer> locationParagraphEnds = new ArrayList<>();
        ArrayList<Integer> locationParagraphIndents = new ArrayList<>();
        int presentBeginPosition, presentEndPosition, textColor;
        double areaColoringDarkenRatio = settings.isUsingNightMode ?
                2 - settings.areaColoringDarkenRatio : // 將顏色調亮
                settings.areaColoringDarkenRatio;  // 將顏色調暗;
        for (GeneralCharacter.SingleLoc loc: chara.areas) {
            if (cityFilter.contains(loc.city)) continue;
            if (contentLoc.length() > 0) contentLoc.append("\n");

            String displayName = loc.city.replace("'", "");
            int paragraphStart = contentLoc.length();
            presentBeginPosition = contentLoc.length();
            contentLoc.append(displayName).append("\t");
            presentEndPosition = contentLoc.length();

            int[] labelColors = new int[settings.isAreaColoring ? loc.colors.size() : 0];
            for (int colorIndex = 0; colorIndex < labelColors.length; colorIndex++) {
                labelColors[colorIndex] = ColorUtil.darken(
                        loc.colors.get(colorIndex),
                        areaColoringDarkenRatio
                );
            }
            contentLoc.setSpan(
                    new LocationLabelSpan(displayName, labelColors),
                    presentBeginPosition,
                    presentEndPosition,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            if (loc.id >= 0) {
                contentLoc.setSpan(
                        new LocationClickSpan(loc.id),
                        presentBeginPosition,
                        presentEndPosition,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }

            for (int i=0; i<loc.prons.size(); i++) {
                if (!hiddenInitials.isEmpty() || !hiddenFinals.isEmpty()
                        || !hiddenTones.isEmpty()) {
                    boolean anyVisible = false;
                    for (GeneralCharacter.SingleLoc.SinglePron p : loc.prons.get(i)) {
                        if (!isPronHidden(p)) { anyVisible = true; break; }
                    }
                    if (!anyVisible) continue;
                }
                if (i>0) {
                    if (!"".equals(loc.notes.get(i-1))) {
                        contentLoc.append("\n");
                        int indentStart = contentLoc.length();
                        contentLoc.append("\t");
                        contentLoc.setSpan(
                                new FixedWidthSpan(LocationLabelSpan.widthEm(displayName)),
                                indentStart,
                                contentLoc.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    } else {
                        contentLoc.append(" · ");
                    }
                }

                int pronunciationUnitStart = contentLoc.length();

                ArrayList<GeneralCharacter.SingleLoc.SinglePron> singleLoc = loc.prons.get(i);
                for (int j=0; j<loc.prons.get(i).size(); j++) {
                    contentLoc.append(j > 0 ? "=" : "");
                    presentBeginPosition = contentLoc.length();
                    contentLoc.append(singleLoc.get(j).syllable());
                    presentEndPosition = contentLoc.length();
                    if (isPlayable(singleLoc.get(j).syllable())) {
                        contentLoc.setSpan(new PronunciationClickSpan(singleLoc.get(j).syllable()),
                                presentBeginPosition, presentEndPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (singleLoc.get(j).coloring!=0) {
                        textColor = ColorUtil.darken(ColorUtil.ithColorInHsv(singleLoc.get(j).coloring, colorCount),
                                areaColoringDarkenRatio
                        );
                        contentLoc.setSpan(new ForegroundColorSpan(textColor),
                                presentBeginPosition, presentEndPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                }

                String ipaSample = loc.prons.get(i).get(0).ipa;
                if (settings.isPresentIpa && !"".equals(ipaSample)) {
                    boolean isMarkNeeded =  !(ipaSample.startsWith("[") || ipaSample.startsWith("("));
                    contentLoc.append(" ");
                    presentBeginPosition = contentLoc.length();
                    if (isMarkNeeded) contentLoc.append("/");
                    int grayColor = Color.parseColor("#777777");
                    for (int j = 0; j < loc.prons.get(i).size(); j++) {
                        if (j > 0) {
                            contentLoc.append("=");
                            if (settings.isAreaColoring) {
                                contentLoc.setSpan(new ForegroundColorSpan(grayColor),
                                        contentLoc.length() - 1, contentLoc.length(),
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                        int alternativeStart = contentLoc.length();
                        contentLoc.append(singleLoc.get(j).ipa);
                        boolean playable = isPlayable(singleLoc.get(j).syllable());
                        int pronColor = pronunciationColor(playable, settings.isUsingNightMode);
                        contentLoc.setSpan(
                                new PronunciationClickSpan(singleLoc.get(j).syllable(), pronColor),
                                alternativeStart,
                                contentLoc.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    }
                    if (isMarkNeeded) contentLoc.append("/");
                    if (settings.isAreaColoring) {
                        contentLoc.setSpan(new ForegroundColorSpan(grayColor),
                                presentBeginPosition, presentBeginPosition + 1,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        contentLoc.setSpan(new ForegroundColorSpan(grayColor),
                                contentLoc.length() - 1, contentLoc.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    presentEndPosition = contentLoc.length();
                }

                if (!"".equals(loc.notes.get(i))) {
                    presentBeginPosition = contentLoc.length();
                    contentLoc.append(" ").append(loc.notes.get(i));
                    presentEndPosition = contentLoc.length();
                    contentLoc.setSpan(new RelativeSizeSpan(0.75f),
                            presentBeginPosition, presentEndPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                contentLoc.setSpan(
                        new NoBreakCandidateSpan(LocationLabelSpan.widthEm(displayName)),
                        pronunciationUnitStart,
                        contentLoc.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }

            float locationTextSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    14,
                    Resources.getSystem().getDisplayMetrics()
            );
            locationParagraphStarts.add(paragraphStart);
            locationParagraphEnds.add(contentLoc.length());
            locationParagraphIndents.add(Math.round(
                    locationTextSize * LocationLabelSpan.widthEm(displayName)
            ));
        }

        for (int i = 0; i < locationParagraphStarts.size(); i++) {
            int end = locationParagraphEnds.get(i);
            if (end < contentLoc.length() && contentLoc.charAt(end) == '\n') {
                end++;
            }
            contentLoc.setSpan(
                    new LeadingMarginSpan.Standard(0, locationParagraphIndents.get(i)),
                    locationParagraphStarts.get(i),
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return new Spanned[]{
                new SpannableString(charaHead),
                new SpannableString(""),
                contentCharaInfo, contentWanshyu, contentLoc
        };
    }

    private static boolean isBookFiltered(String bookFilter) {
        return cityFilter.contains("韻書") || cityFilter.contains(bookFilter);
    }

    private static void appendBook(SpannableStringBuilder output, String name,
                                   ArrayList<String> entries) {
        name = ChineseTextConverter.display(name);
        int labelStart = output.length();
        output.append(name);
        output.setSpan(new BookLabelSpan(), labelStart, output.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        output.append(" ");
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) output.append(" | ");
            output.append(ChineseTextConverter.display(entries.get(index)));
        }
    }


    public int length() {
        return charas.size();
    }


}
