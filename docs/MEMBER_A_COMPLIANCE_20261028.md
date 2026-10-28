# Member A layout/colour compliance revision — 28 October 2026

## Scope and ownership
AI-assisted changes to ThemesActivity, WordListActivity, FlashcardActivity and QuizActivity and their resources. This is not a claim of sole student authorship, a completed human peer review, or guaranteed marks. Confirm permitted AI scope before submission.

## Changes
- Real ConstraintLayout parent/child constraints in all four Activity XML files, with resource-based maximum content width (480dp), anchored headers/footers and match-constraint content regions.
- Theme tiles flow vertically with intrinsic height instead of overlapping independent top/bottom content. Narrow displays (below 360dp) or font scale >=1.3 use one grid column. Normal displays retain two columns.
- Flashcards bind 36 AI-generated vector meaning illustrations from words.json. Card contents scroll rather than overlap under large fonts; NEXT remains outside the scrollable card.
- Quiz options use ConstraintLayout chains. Available heights below 700dp use an 80dp prompt and 24sp option glyphs (still scaling with user font settings). Feedback wraps and grows rather than clipping its second line.
- Answered Hanzi AND pinyin use on_mint/on_clay roles (light ratios 6.08:1 / 5.27:1; dark 7.65:1 / 8.30:1). Normal muted/card ratio 5.95:1 light and 8.35:1 dark. These are configured text-pair checks, not whole-app accessibility certification.
- Back button name and polite feedback announcements added.
- Retained FlashcardViewModel owns asynchronous session initialization, retaining its in-flight guard/result across Activity recreation; a known restored ID is read from the store rather than inferred absent from a null snapshot.
- Quiz option order is seeded by session ID, question position and word ID. Stable for the same content version; content migration across application updates is not covered.
- res/raw/keep_word_illustrations.xml preserves runtime-resolved images in shrunk Release builds. Prior Release output discarded all 36; the first corrected resource-table inspection retained all 36.

## Persistent regressions
- MemberADesignTest: four screens/answered quiz visible controls and text fit; new QuizViewModel restores identical options and saved answer.
- FlashcardRegressionTest: SESSION and BROWSE recreation retain index and ID; initialization interrupted by recreation creates exactly one session; fresh ViewModel restores a known session when snapshot is null.
- QuizGeneratorTest: domain generation and deterministic-seed checks. The domain test is not a substitute for the real ViewModel device checks.

## Verification and known boundaries
- First compact-display run exposed undersized answered options. Subsequent image inspection also exposed feedback clipping and overlapping theme-tile text; those issues were corrected rather than treating an initially successful test as sufficient.
- Final run: 38/38 JVM tests; 6/6 targeted Member A device regressions in each of normal light, compact 320×640dp with 1.3 font scale, and normal dark configurations (18 executions of 6 unique cases). Debug/Release builds and lint task succeeded. Configurations restored afterwards. Results are in work/member_a_compliance/device_*_final.log and build_final.log; screenshots are in screens_normal, screens_small and screens_night. Release resource-table inspection retained all 36 word illustrations. This does not supersede the failed whole-project test below.
- A whole-project default-display run before the last layout refinements ran 20 tests with one failure in CulturePlaybackTest (Member B navigation); see device_normal.log. This is not a passing whole-project regression and is not hidden by the targeted Member A results.
- API 30 runtime testing, manual TalkBack, real child usability evaluation and actual OS process-kill recovery have not been performed. Recreating an Activity and constructing a new ViewModel do not prove all process-death behaviour.
- Only one Android 17/API 37 emulator image is installed. No additional SDK/emulator downloads were performed.
- Artwork provides simplified semantic cues; family/abstract symbols are not evidence of tested recognisability by preschool children. Audio quality and Member B content are outside this revision.
- Build success may retain Lint warnings; check the lint report rather than equating task success with zero warnings.

## Submission
Use the final report revision with its explicit evidence limitations. Original course-agent transcripts, actual peer feedback, Kaltura video and the permitted AI-use confirmation remain student-supplied items.
