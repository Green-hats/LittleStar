package com.littlestar.app;

import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.feature.flashcard.FlashcardActivity;
import com.littlestar.app.feature.quiz.QuizActivity;
import com.littlestar.app.feature.quiz.QuizViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/** Real View controls and real ExoPlayer missing-asset errors; no production test hooks. */
@RunWith(AndroidJUnit4.class)
public class PeerReviewLearningTest {
 private AppContainer c;
 @Before public void prepare() throws Exception {
  c=((LittleStarApp)ApplicationProvider.getApplicationContext()).container();
  c.content.ensureLoaded();c.settings.setOnboarded(true);c.settings.setSoundEnabled(false);
  c.store.update(s->{
   s.words.clear();s.sessions.clear();s.dailyPlans.clear();
   ProgressState.Session q=new ProgressState.Session();q.id=880;q.kind="THEME";
   q.date=c.clock.today().toString();q.themeId="animals";q.quizWordIds=List.of("mao","gou");q.total=2;
   s.sessions.add(q);s.nextSessionId=881;
  });
  read(s->true);
 }
 private Intent browse(){return new Intent(ApplicationProvider.getApplicationContext(),FlashcardActivity.class).putExtra("mode","BROWSE").putStringArrayListExtra("wordIds",new ArrayList<>(List.of("mao","gou","niao")));}
 private Intent quiz(){return new Intent(ApplicationProvider.getApplicationContext(),QuizActivity.class).putExtra("mode","THEME").putExtra("sessionId",880L);}

 @Test public void optionalRecord_isAlignedEnglishButton_andNextNeedsNoMark() throws Exception {
  try(ActivityScenario<FlashcardActivity> s=ActivityScenario.launch(browse())) {
   waitFor(s,R.id.stamp,View.VISIBLE);
   UiEvidence.capture("after_stamp_optional");
   s.onActivity(a->{
    assertEquals(a.getString(R.string.pr_stamp_optional),((TextView)a.findViewById(R.id.stampLabel)).getText().toString());
    assertEquals("android.widget.Button",a.findViewById(R.id.stamp).getAccessibilityClassName());
    View record=a.findViewById(R.id.stamp), next=a.findViewById(R.id.btnNext);
    assertEquals("Buttons must align",record.getTop(),next.getTop());
    assertEquals("Buttons must have equal heights",record.getHeight(),next.getHeight());
    assertTrue(record.getHeight()>=a.getResources().getDimensionPixelSize(R.dimen.ls_btn_primary));
    assertStaticCard(a.findViewById(R.id.pageCard));
    assertNoParentExplanation(a.findViewById(android.R.id.content));
   });
   onView(withId(R.id.btnNext)).perform(click());
   assertTrue(read(st->st.words.isEmpty()));
   onView(withId(R.id.stamp)).perform(click());
   Long learned=read(st->st.words.get("gou").learnedAt);assertNotNull(learned);
   onView(withId(R.id.stamp)).perform(click());
   assertEquals(learned,read(st->st.words.get("gou").learnedAt));
   s.onActivity(a->assertEquals(a.getString(R.string.pr_stamp_marked),((TextView)a.findViewById(R.id.stampLabel)).getText().toString()));
   UiEvidence.capture("after_stamp_marked");
   onView(withId(R.id.btnNext)).perform(click());
   s.onActivity(a->assertEquals(a.getString(R.string.fc_back_words),((TextView)a.findViewById(R.id.btnNextText)).getText().toString()));
  }
 }
 @Test public void flashcardMissingAudio_retryRecovers_withoutProgressChange() throws Exception {
  Word w=c.content.wordById("mao");String original=w.audio;w.audio="audio/words/deliberately-missing-peer-review.ogg";
  try(ActivityScenario<FlashcardActivity> s=ActivityScenario.launch(browse())) {
   waitFor(s,R.id.audioMissing,View.VISIBLE);
   UiEvidence.capture("after_flashcard_missing_audio");
   assertTrue(read(st->st.words.isEmpty()));
   s.onActivity(a->w.audio=original);
   onView(withId(R.id.btnAudioRetry)).perform(click());
   waitFor(s,R.id.audioMissing,View.GONE);
   assertTrue(read(st->st.words.isEmpty()));
   UiEvidence.capture("after_flashcard_audio_retry");
  } finally {w.audio=original;}
 }
 @Test public void quizMissingAudio_retryPreservesQuestionAndAnswers() throws Exception {
  Word w=c.content.wordById("mao");String original=w.audio;w.audio="audio/words/deliberately-missing-peer-review.ogg";
  try(ActivityScenario<QuizActivity> s=ActivityScenario.launch(quiz())) {
   waitFor(s,R.id.audioMissing,View.VISIBLE);
   UiEvidence.capture("after_quiz_missing_audio");
   assertEquals(0,(int)read(st->st.sessions.get(0).answers.size()));
   AtomicReference<String> counter=new AtomicReference<>();
   s.onActivity(a->{counter.set(((TextView)a.findViewById(R.id.quizCounter)).getText().toString());w.audio=original;});
   onView(withId(R.id.btnAudioRetry)).perform(click());
   waitFor(s,R.id.audioMissing,View.GONE);
   s.onActivity(a->assertEquals(counter.get(),((TextView)a.findViewById(R.id.quizCounter)).getText().toString()));
   assertEquals(0,(int)read(st->st.sessions.get(0).answers.size()));
   UiEvidence.capture("after_quiz_audio_retry");
  } finally {w.audio=original;}
 }
 @Test public void quizMissingAudio_leaveDoesNotSubmitAnswer() throws Exception {
  Word w=c.content.wordById("mao");String original=w.audio;w.audio="audio/words/deliberately-missing-peer-review.ogg";
  try(ActivityScenario<QuizActivity> s=ActivityScenario.launch(quiz())) {
   waitFor(s,R.id.audioMissing,View.VISIBLE);
   onView(withId(R.id.btnAudioContinue)).perform(click());
   assertEquals(0,(int)read(st->st.sessions.get(0).answers.size()));
   long deadline=SystemClock.elapsedRealtime()+5000;
   while(s.getState()!=androidx.lifecycle.Lifecycle.State.DESTROYED && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100);
   assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,s.getState());
  } finally {w.audio=original;}
 }
 @Test public void retry_preservesFirstAnswerAndSchedule_acrossRecreation() throws Exception {
  try(ActivityScenario<QuizActivity> s=ActivityScenario.launch(quiz())) {
   waitFor(s,R.id.option0,View.VISIBLE);
   AtomicReference<QuizViewModel> model=new AtomicReference<>();
   s.onActivity(a->{
    QuizViewModel vm=new ViewModelProvider(a).get(QuizViewModel.class);model.set(vm);
    assertNotNull(vm.state().getValue().question);
    vm.choose((vm.state().getValue().question.correctIndex()+1)%4);
   });
   String wordId=model.get().state().getValue().question.wordId();
   String due=read(st->st.words.get(wordId).dueDate);
   assertEquals(1,(int)read(st->st.sessions.get(0).answers.size()));
   s.onActivity(a->a.findViewById(R.id.btnRetry).performClick());
   s.recreate();
   s.onActivity(a->{
    QuizViewModel vm=new ViewModelProvider(a).get(QuizViewModel.class);model.set(vm);
    assertTrue(vm.state().getValue().retrying);
    assertFalse(((TextView)a.findViewById(R.id.feedbackText)).getText().toString().contains(vm.state().getValue().question.english()));
    vm.choose((vm.state().getValue().question.correctIndex()+1)%4);
    assertTrue(vm.state().getValue().retrying);
    vm.choose(vm.state().getValue().question.correctIndex());
    assertTrue(vm.state().getValue().corrected);
    assertFalse(vm.state().getValue().lastAnswerCorrect);
   });
   assertEquals(1,(int)read(st->st.sessions.get(0).answers.size()));
   assertEquals(0,(int)read(st->st.sessions.get(0).correct));
   assertFalse(read(st->st.sessions.get(0).answers.get(0).correct));
   assertTrue(read(st->st.sessions.get(0).answers.get(0).firstTry));
   assertEquals(due,read(st->st.words.get(wordId).dueDate));
   assertNull(read(st->st.words.get(wordId).learnedAt));
   s.recreate();
   s.onActivity(a->{
    QuizViewModel vm=new ViewModelProvider(a).get(QuizViewModel.class);
    assertTrue(vm.state().getValue().corrected);
    vm.next();assertEquals(1,vm.state().getValue().index);
    assertFalse(vm.state().getValue().retrying);assertFalse(vm.state().getValue().corrected);
   });
  }
 }
 @Test public void quizPromptAndOptions_doNotOverlapAtDoubleFontScale() throws Exception {
  float original=android.provider.Settings.System.getFloat(ApplicationProvider.getApplicationContext().getContentResolver(),android.provider.Settings.System.FONT_SCALE,1f);
  try {
   setFontScale(2f);
   try(ActivityScenario<QuizActivity> s=ActivityScenario.launch(quiz())) {
    AtomicBoolean ready=new AtomicBoolean();long deadline=SystemClock.uptimeMillis()+5000;
    do {
     s.onActivity(a->ready.set(a.getResources().getConfiguration().fontScale>=2f && a.findViewById(R.id.questionHint).getWidth()>0));
     if(ready.get())break;SystemClock.sleep(50);
    }while(SystemClock.uptimeMillis()<deadline);
    assertTrue("Expected font2 configuration",ready.get());
    s.onActivity(a->{
     View illustration=a.findViewById(R.id.questionIllustration), hint=a.findViewById(R.id.questionHint);
     int[] imagePosition=new int[2],hintPosition=new int[2];
     illustration.getLocationOnScreen(imagePosition);hint.getLocationOnScreen(hintPosition);
     assertTrue("Prompt must be below illustration",hintPosition[1]>=imagePosition[1]+illustration.getHeight());
     TextView text=(TextView)hint;
     assertTrue(text.getLayout().getHeight()<=text.getHeight());
     for(int id:new int[]{R.id.option0,R.id.option1,R.id.option2,R.id.option3}) {
      assertTrue(a.findViewById(id).getHeight()>=a.getResources().getDimensionPixelSize(R.dimen.ls_quiz_option_min));
     }
    });
   }
  }finally{setFontScale(original);}
 }
 @Test public void flashcard_noGesturePaging_andListenFitsInsideCard() throws Exception {
  try(ActivityScenario<FlashcardActivity> s=ActivityScenario.launch(browse())) {
   waitFor(s,R.id.pageHanzi,View.VISIBLE);
   AtomicReference<String> before=new AtomicReference<>();
   s.onActivity(a->before.set(((TextView)a.findViewById(R.id.cardCounter)).getText().toString()));
   onView(withId(R.id.pageCard)).perform(androidx.test.espresso.action.ViewActions.swipeLeft());
   onView(withId(R.id.pageCard)).perform(androidx.test.espresso.action.ViewActions.swipeUp());
   s.onActivity(a->{
    assertEquals(before.get(),((TextView)a.findViewById(R.id.cardCounter)).getText().toString());
    assertListenInsideCard(a);
   });
  }
 }
 @Test public void flashcard_doubleFont_keepsAlignedButtonsAndReadableListen() throws Exception {
  float original=android.provider.Settings.System.getFloat(ApplicationProvider.getApplicationContext().getContentResolver(),android.provider.Settings.System.FONT_SCALE,1f);
  try {
   try(ActivityScenario<FlashcardActivity> s=ActivityScenario.launch(browse())) {
    waitFor(s,R.id.pageHanzi,View.VISIBLE);
    setActivityFont(s,2f);
    s.onActivity(a->{
     View viewport=a.findViewById(R.id.flashcardScroll);
     var params=(androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)viewport.getLayoutParams();
     float density=a.getResources().getDisplayMetrics().density;
     params.width=Math.round(360*density);params.height=Math.round(440*density);
     viewport.setLayoutParams(params);
    });
    onView(withId(R.id.btnListen)).perform(androidx.test.espresso.action.ViewActions.scrollTo());
    s.onActivity(PeerReviewLearningTest::assertListenInsideCard);
    onView(withId(R.id.stamp)).perform(androidx.test.espresso.action.ViewActions.scrollTo());
    s.onActivity(a->{
     assertTrue(a.getResources().getConfiguration().fontScale>=2f);
     View record=a.findViewById(R.id.stamp),next=a.findViewById(R.id.btnNext);
     assertEquals(record.getTop(),next.getTop());assertEquals(record.getHeight(),next.getHeight());
     assertEquals(android.widget.LinearLayout.HORIZONTAL,((android.widget.LinearLayout)a.findViewById(R.id.pageMain)).getOrientation());
     View footer=a.findViewById(R.id.flashcardFooter),root=a.findViewById(R.id.flashcardRoot),viewport=a.findViewById(R.id.flashcardScroll);
     assertTrue("Compact layout should fit one screen",footer.getBottom()+root.getPaddingBottom()<=viewport.getHeight());
     TextView hanzi=a.findViewById(R.id.pageHanzi);
     assertTrue("Bounded viewport should adapt text size",hanzi.getTextSize()<a.getResources().getDimension(R.dimen.ls_flashcard_hanzi_max));
     assertTrue(hanzi.getTextSize()>=a.getResources().getDimension(R.dimen.ls_flashcard_hanzi_min));
     assertStaticCard(a.findViewById(R.id.pageCard));
     assertNoParentExplanation(a.findViewById(android.R.id.content));
    });
   }
  }finally{setFontScale(original);}
 }
 @Test public void flashcard_landscapeBlocksLearning_andPortraitRestoresCurrentRecord() throws Exception {
  float original=android.provider.Settings.System.getFloat(ApplicationProvider.getApplicationContext().getContentResolver(),android.provider.Settings.System.FONT_SCALE,1f);
  try(ActivityScenario<FlashcardActivity> s=ActivityScenario.launch(browse())) {
   waitFor(s,R.id.pageHanzi,View.VISIBLE);setActivityFont(s,2f);
   s.onActivity(a->{a.findViewById(R.id.btnNext).performClick();a.findViewById(R.id.stamp).performClick();});
   c.store.flushNow();
   AtomicReference<String> word=new AtomicReference<>(),counter=new AtomicReference<>();
   s.onActivity(a->{
    word.set(((TextView)a.findViewById(R.id.pageHanzi)).getText().toString());
    counter.set(((TextView)a.findViewById(R.id.cardCounter)).getText().toString());
    a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
   });
   waitFor(s,R.id.rotationGate,View.VISIBLE);
   onView(withId(R.id.rotationMessage)).check(androidx.test.espresso.assertion.ViewAssertions.matches(
    androidx.test.espresso.matcher.ViewMatchers.withText(R.string.fc_portrait_only)));
   onView(withId(R.id.rotationMessage)).check(androidx.test.espresso.assertion.ViewAssertions.matches(
    androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed()));
   s.onActivity(a->{
    assertEquals(android.content.res.Configuration.ORIENTATION_LANDSCAPE,a.getResources().getConfiguration().orientation);
    assertEquals(View.GONE,a.findViewById(R.id.flashcardScroll).getVisibility());
    assertFalse(a.findViewById(R.id.btnNext).isShown());assertFalse(a.findViewById(R.id.stamp).isShown());
    assertFalse(a.findViewById(R.id.btnListen).isShown());
    var vm=new androidx.lifecycle.ViewModelProvider(a).get(com.littlestar.app.feature.flashcard.FlashcardViewModel.class);
    int index=vm.currentIndex();
    a.findViewById(R.id.btnNext).performClick();a.findViewById(R.id.stamp).performClick();
    a.findViewById(R.id.btnListen).performClick();
    assertEquals("Landscape actions must be ignored",index,vm.currentIndex());
    assertFalse(Boolean.TRUE.equals(c.speech.isPlaying().getValue()));
    a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
   });
   waitFor(s,R.id.flashcardScroll,View.VISIBLE);
   onView(withId(R.id.pageHanzi)).check(androidx.test.espresso.assertion.ViewAssertions.matches(
    androidx.test.espresso.matcher.ViewMatchers.withText(word.get())));
   s.onActivity(a->{
    assertEquals(View.GONE,a.findViewById(R.id.rotationGate).getVisibility());
    assertEquals(counter.get(),((TextView)a.findViewById(R.id.cardCounter)).getText().toString());
    assertEquals(a.getString(R.string.pr_stamp_marked),((TextView)a.findViewById(R.id.stampLabel)).getText().toString());
   });
  }finally{setFontScale(original);}
 }
 private static void assertListenInsideCard(android.app.Activity a) {
  View card=a.findViewById(R.id.pageCard),listen=a.findViewById(R.id.btnListen);
  int[] cardPosition=new int[2],listenPosition=new int[2];
  card.getLocationOnScreen(cardPosition);listen.getLocationOnScreen(listenPosition);
  assertTrue(listenPosition[1]>=cardPosition[1]);
  assertTrue("Listen must not cross card bottom",listenPosition[1]+listen.getHeight()<=cardPosition[1]+card.getHeight());
  android.graphics.Rect visible=new android.graphics.Rect();assertTrue(listen.getGlobalVisibleRect(visible));
  assertEquals("Listen must be fully visible",listen.getHeight(),visible.height());
 }
 private static void assertStaticCard(View v) {
  assertFalse(v instanceof androidx.core.widget.NestedScrollView);
  assertFalse(v instanceof androidx.viewpager2.widget.ViewPager2);
  if(v instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)assertStaticCard(group.getChildAt(i));
 }
 private static void assertNoParentExplanation(View v) {
  if(v instanceof TextView text)assertFalse(text.getText().toString().contains("For parents"));
  if(v instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)assertNoParentExplanation(group.getChildAt(i));
 }
 private static void setFontScale(float scale) throws Exception {
  var descriptor=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation()
    .executeShellCommand("settings put system font_scale "+scale);
  try(var input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){while(input.read()!=-1){}}
  long deadline=SystemClock.elapsedRealtime()+5000;
  do {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
   float actual=android.provider.Settings.System.getFloat(ApplicationProvider.getApplicationContext().getContentResolver(),android.provider.Settings.System.FONT_SCALE,1f);
   if(Math.abs(actual-scale)<.01f)return;
   SystemClock.sleep(50);
  }while(SystemClock.elapsedRealtime()<deadline);
  fail("Font scale did not reach "+scale);
 }
 private static <A extends android.app.Activity> void waitForFont(ActivityScenario<A> scenario,float scale) {
  long deadline=SystemClock.elapsedRealtime()+5000;AtomicBoolean ready=new AtomicBoolean();
  do {
   scenario.onActivity(a->ready.set(Math.abs(a.getResources().getConfiguration().fontScale-scale)<.01f));
   if(ready.get())return;SystemClock.sleep(50);
  }while(SystemClock.elapsedRealtime()<deadline);
  fail("Activity font scale did not reach "+scale);
 }
 private static <A extends android.app.Activity> void setActivityFont(ActivityScenario<A> scenario,float scale) throws Exception {
  setFontScale(1f);waitForFont(scenario,1f);
  setFontScale(scale);waitForFont(scenario,scale);
 }
 private <T>T read(Function<ProgressState,T> f) throws Exception {
  CountDownLatch done=new CountDownLatch(1);AtomicReference<T> result=new AtomicReference<>();
  c.store.read(f,value->{result.set(value);done.countDown();});assertTrue(done.await(10,TimeUnit.SECONDS));return result.get();
 }
 private static <A extends android.app.Activity> void waitFor(ActivityScenario<A> s,int id,int visibility) {
  long deadline=SystemClock.elapsedRealtime()+10000;AtomicBoolean ready=new AtomicBoolean();
  do {
   s.onActivity(a->{View v=a.findViewById(id);ready.set(v!=null&&v.getVisibility()==visibility);});
   if(ready.get())return;SystemClock.sleep(100);
  }while(SystemClock.elapsedRealtime()<deadline);
  fail("View did not reach expected visibility: "+id+" -> "+visibility);
 }
}
