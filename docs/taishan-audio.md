# Taishan reference audio

## Source and Boundaries

- Printed dictionary: Huang Jianyun's Taishan dictionary, supported only through user-imported JSON. The public source and APK do not redistribute the extracted book dataset.
- Online source: Stephen Li's Taishanese Dictionary, <https://www.taishandict.com/>.
- Lookup uses its public search form, selecting **Taishanese**, not Mandarin, and filters returned headwords exactly after simplified/traditional conversion.
- Old `stephen-li.com` URLs must not be used: the domain no longer serves the dictionary.
- Recordings and source definitions remain independent of the printed dictionary. No local IPA or definition is rewritten.
- No permission to bulk redistribute the recordings was found. The implementation is on-demand reference access with source attribution and temporary app caching, not a licensed audio dataset. Confirm permission before mirroring, shipping offline audio, or relying on this as a permanent production service.

## Online Dictionary Search

The Taishan page has separate **Local dictionary** and **Website dictionary** modes. Local mode searches the bundled printed dictionary only. Website mode submits the site's public `MySQLSearch.php` form on demand and can search Taishanese, IPA, Cantonese, Mandarin, English, or description, with partial or full matching. Results are parsed into native app rows with source headword, transcription, language equivalents, English gloss, description, and the source recording link when present. Search results are not bundled or bulk-cached in the app. The source currently caps broad responses at 100 displayed entries; the UI indicates that cap. This integration depends on the public HTML form and can stop working if the site changes or becomes unavailable. It does not imply permission to mirror or redistribute the source database.

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

Local build verification on 2026-10-08: the offline `testDebugUnitTest` run passed all 35 enabled JVM tests (39 reported including four opt-in live tests skipped by default). Separately, all four opt-in network smoke tests passed, including playable audio checks, confirmed dead links, and a valid zero-result response. `assembleDebug` produced version 0.8.24 / code 824 for `cc.ecisr.jyutdict.dual`, with the existing update signing certificate SHA-256 `d9186624cfa4575aa965b67164fd473737acb6d633a249740532901b4876fdfd`. The private update workflow reruns the offline tests, embeds its build number, and requires this certificate before publishing. Android lint reports six existing errors outside this feature (Android 6 API compatibility in character/location/update code and a link-button style, plus two format-string errors); these have not been suppressed or changed by the audio integration.

The website-dictionary integration adds parser tests for ordinary entries, descriptions, zero matches, and the 100-entry cap. The opt-in `TaishanAudioLiveTest.liveOnlineDictionarySearchParsesFullEntriesAndFieldSearch` smoke test checks current live responses for a Taishanese partial query and an English full query.

Before distributing, test on Android 6 and a current Android device: unique read, mismatched read, multiple meanings, no result, offline/retry, cancellation while loading, backgrounding, rotation, dark/light themes, large fonts, and local import/replace/delete. Confirm audio focus and no playback after leaving. Network checks do not replace listening by a native speaker or device UI testing.
