# Taishan reference audio

## Source and Boundaries

- Printed dictionary: Huang Jianyun's Taishan dictionary, supported only through user-imported JSON. The public source and APK do not redistribute the extracted book dataset.
- Online source: Stephen Li's Taishanese Dictionary, <https://www.taishandict.com/>.
- Lookup uses its public search form, selecting **Taishanese**, not Mandarin, and filters returned headwords exactly after simplified/traditional conversion.
- Old `stephen-li.com` URLs must not be used: the domain no longer serves the dictionary.
- Recordings and source definitions remain independent of the printed dictionary. No local IPA or definition is rewritten.
- No permission to bulk redistribute the recordings was found. The implementation is on-demand reference access with source attribution and temporary app caching, not a licensed audio dataset. Confirm permission before mirroring, shipping offline audio, or relying on this as a permanent production service.

## Online Dictionary Search

The Taishan page has separate **Local dictionary** and **Website dictionary** modes. Local mode searches a user-imported dictionary; the public APK does not bundle the printed dictionary. Website mode submits the site's public `MySQLSearch.php` form on demand and can search Taishanese, IPA, Cantonese, Mandarin, English, or description, with partial or full matching. Results are parsed into native app rows with source headword, transcription, language equivalents, English gloss, description, and the source recording link when present. Search results are not bundled or bulk-cached in the app. The source currently caps broad responses at 100 displayed entries; the UI indicates that cap. This integration depends on the public HTML form and can stop working if the site changes or becomes unavailable. It does not imply permission to mirror or redistribute the source database.

## Chinese Meanings

Website rows and recording candidates show the source's Mandarin Chinese equivalent first, with a Chinese Cantonese fallback when available. English glosses and supplemental descriptions remain unchanged in the source model and are collapsed under an English-original action. Entries without a Chinese equivalent say so explicitly rather than labeling English text as Chinese.

For English supplemental text, users can request a Chinese machine translation. The first request requires confirmation that the public English gloss and description will be sent to MyMemory (<https://mymemory.translated.net/>); local dictionary entries, favorites and recordings are not sent. Translation is not automatic or a bulk dataset conversion. Results are labeled as machine translation, follow the app's simplified/traditional display setting, and never replace source definitions or IPA. Successful translations use the existing app disk cache with a 30-day freshness limit; Android may evict that cache earlier. MyMemory availability, accuracy and public-service quotas are outside the app's control. Failure leaves lookup and playback available and provides a retry action. The service needs no user API key.

## Matching and Playback

User-imported recordings take priority. Otherwise, a speaker tap queries the exact headword. One unique, complete, compatible IPA text match may play directly; other results require explicit preview selection with their own IPA and meaning. Tone differences, partial IPA, ambiguous alternatives, and the source's simplified stop/affricate conventions are never silently converted. Matching transcription text does not certify accent or meaning.

The reference-status action opens all source candidates even when a unique text match exists. Words use whole-word recordings; unavailable words are not constructed by concatenating character audio or played using generic Cantonese TTS. Entries with missing headwords can still use an imported recording. A source response with `Number of matches: 0` is a valid no-recording result; the site currently prefixes it with explanatory prose, so callers must not require the count to start the response.

Requests stop on a second tap, a different selection, audio import, or leaving the activity. Lookups run separately from dictionary filtering/import. Successful HTML lookups are cached for seven days, at most 20 words. Confirmed 404/410 audio URLs are excluded from cached and in-memory candidates for seven days, so a dead source link is not retried on every tap. The Taishan MP3 cache is limited to approximately 30 MB plus the current download.

The in-memory lookup cache retains at most 40 words. Multiple bracketed readings and distinct source senses stay separate and cannot silently become one automatic match.

All recording links must resolve to HTTPS on `www.taishandict.com`, without redirects, credentials, ports, traversal, queries, or fragments. Downloads are bounded to 2 MB and validated for audio content and an Android-decodable stream of at most two minutes before playback.

## Verification

Use JDK 21 for current Android lint (the checker uses APIs unavailable in JDK 17):

```powershell
$env:JAVA_HOME='D:\Android\jdk\jdk-21.0.12.1+1'
.\gradlew.bat lintDebug --console=plain
```

This Windows workspace currently runs Gradle's unit-test launcher reliably with JDK 17:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

Opt-in network smoke checks make five word lookups and five audio-header checks; normal tests never access the site:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:TAISHAN_LIVE_TESTS='1'
.\gradlew.bat testDebugUnitTest --tests cc.ecisr.jyutdict.TaishanAudioLiveTest --rerun-tasks --console=plain
Remove-Item Env:TAISHAN_LIVE_TESTS
```

On 2026-10-08, the two pronoun recordings, the whole-word greeting, and a Taishan entry's recording passed MP3 header checks. A 28-link HEAD smoke sample across common words returned `audio/mpeg` for 24 links; four did not, including the separately confirmed HTTP 404 Sky and Moonlight links. HEAD results are only a sample, not a complete health check. The live test asserts the two confirmed dead links and a valid zero-result search response.

The printed dictionary describes its notation as IPA and gives an inventory of 19 initials, 38 finals, and five tone classes with eight citation/changed tone values. Entries concatenate syllables (4,108 of 4,521 have multiple tone-marked syllables) and some contain changed tones, so conversion needs syllable parsing and tone handling, not simple text-to-speech of the letters. IPA can represent the target phonemes, but Android does not have a Taishanese voice merely because it accepts IPA-like text. Credible synthesis needs a Taishanese acoustic model or a validated rule-based voice built from licensed speaker recordings and checked with speakers; generic Mandarin/Cantonese TTS is not a substitute. Public repositories found during this investigation included text dictionaries/input-method data, not a ready-to-use licensed Taishanese voice. The app therefore keeps whole-word native recordings, local user-provided recordings, and clear no-audio states rather than inventing a pronunciation.

Historical verification on 2026-10-08: the offline `testDebugUnitTest` run passed all 35 enabled JVM tests (39 reported including four opt-in live tests skipped by default). Separately, all four opt-in network smoke tests passed, including playable audio checks, confirmed dead links, and a valid zero-result response. `assembleDebug` produced version 0.8.24 / code 824 for `cc.ecisr.jyutdict.dual`, with the existing update signing certificate SHA-256 `d9186624cfa4575aa965b67164fd473737acb6d633a249740532901b4876fdfd`. That version still had existing lint errors outside the audio feature.

Public-release verification on 2026-10-15: 54 JVM tests passed and five optional live tests were skipped (59 reported, no failures). Version 0.8.27 / code 827 builds successfully with the same package ID and signing certificate. Android lint now reports zero errors and 30 warnings after Android 6 API and format-string fixes. The public workflow runs tests, lint and APK assembly, verifies the certificate, and rejects APKs containing the unlicensed book dataset before publishing. No Android device was connected for installation or UI verification in this run.

Chinese-meaning verification on 2026-10-16: the unit suite passed 64 enabled tests (69 reported, five optional network tests skipped). New tests cover Chinese-first selection, preserving English originals, UTF-8 translation chunk limits, rejection of quota/error responses, and light/dark recycled rows at a narrow width. A separate live MyMemory request translated `Name of a county` to Chinese successfully; this is a sample, not an availability or translation-quality guarantee. The only attached emulator was offline, so device installation and interactive UI verification remain untested.

The website-dictionary integration adds parser tests for ordinary entries, descriptions, zero matches, and the 100-entry cap. The opt-in `TaishanAudioLiveTest.liveOnlineDictionarySearchParsesFullEntriesAndFieldSearch` smoke test checks current live responses for a Taishanese partial query and an English full query.

Before distributing, test on Android 6 and a current Android device: unique read, mismatched read, multiple meanings, no result, offline/retry, cancellation while loading, backgrounding, rotation, dark/light themes, large fonts, and local import/replace/delete. Confirm audio focus and no playback after leaving. Network checks do not replace listening by a native speaker or device UI testing.
