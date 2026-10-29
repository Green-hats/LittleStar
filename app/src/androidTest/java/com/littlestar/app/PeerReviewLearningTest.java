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

 @Test public void optionalStamp_hasParentExplanation_andNextNeedsNoMark() throws Exception {
  try(ActivityScenario<FlashcardActivity> s=ActivityScenario.launch(browse())) {
   waitFor(s,R.id.stamp,View.VISIBLE);
   UiEvidence.capture("after_stamp_optional");
   s.onActivity(a->{
    assertEquals(a.getString(R.string.pr_stamp_optional),((TextView)a.findViewById(R.id.stampLabel)).getText().toString());
    assertTrue(((TextView)a.findViewById(R.id.stampExplanation)).getText().toString().contains("not mastery"));
    assertEquals("android.widget.Button",a.findViewById(R.id.stamp).getAccessibilityClassName());
    int[] hint=new int[2], label=new int[2];
    a.findViewById(R.id.stampExplanation).getLocationOnScreen(hint);
    a.findViewById(R.id.stampLabel).getLocationOnScreen(label);
    assertTrue("Parent explanation must not overlap stamp row",hint[1]>=label[1]+a.findViewById(R.id.stampLabel).getHeight());
   });
   onView(withId(R.id.btnNext)).perform(click());
   assertTrue(read(st->st.words.isEmpty()));
   onView(withId(R.id.stamp)).perform(click());
   Long learned=read(st->st.words.get("gou").learnedAt);assertNotNull(learned);
   onView(withId(R.id.stamp)).perform(click());
   assertEquals(learned,read(st->st.words.get("gou").learnedAt));
   s.onActivity(a->assertEquals(a.getString(R.string.pr_stamp_marked),((TextView)a.findViewById(R.id.stampLabel)).getText().toString()));
   UiEvidence.capture("after_stamp_marked");
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
