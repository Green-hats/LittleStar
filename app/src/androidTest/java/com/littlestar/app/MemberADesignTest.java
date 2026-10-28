package com.littlestar.app;

import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.SavedStateHandle;
import com.littlestar.app.feature.words.ThemesActivity;
import com.littlestar.app.feature.words.WordListActivity;
import com.littlestar.app.feature.flashcard.FlashcardActivity;
import com.littlestar.app.feature.quiz.QuizActivity;
import com.littlestar.app.feature.quiz.QuizViewModel;
import com.littlestar.app.data.store.ProgressState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/** Persistent regression checks; also run with wm size 960x1920/density480 + font_scale1.3. */
@RunWith(AndroidJUnit4.class)
public class MemberADesignTest {
    private AppContainer c;
    @Before public void seed() throws Exception {
        c=((LittleStarApp)ApplicationProvider.getApplicationContext()).container();
        c.settings.setOnboarded(true); c.settings.setSoundEnabled(false); c.settings.setEnglishHint(false);
        CountDownLatch latch=new CountDownLatch(1);
        c.store.update(s->{
            c.content.ensureLoaded(); s.sessions.clear(); s.words.clear(); s.dailyPlans.clear(); s.usage.clear();
            ProgressState.Session q=new ProgressState.Session(); q.id=901; q.kind="THEME";
            q.date=c.clock.today().toString(); q.quizWordIds=List.of("mao","gou","niao"); q.total=3;
            s.sessions.add(q); s.nextSessionId=902;
        });
        c.store.read(s->true,v->latch.countDown()); assertTrue(latch.await(10,TimeUnit.SECONDS));
    }
    @Test public void fourScreensAndAnsweredQuiz_haveVisibleControls() throws Exception {
        try(ActivityScenario<ThemesActivity> s=ActivityScenario.launch(ThemesActivity.class)) {
            SystemClock.sleep(700); s.onActivity(a->{ assertVisible(a.findViewById(R.id.btnBack)); assertVisible(a.findViewById(R.id.themesList)); });
            UiEvidence.capture("A1_checked");
        }
        Intent words=new Intent(ApplicationProvider.getApplicationContext(),WordListActivity.class).putExtra("themeId","animals");
        try(ActivityScenario<WordListActivity> s=ActivityScenario.launch(words)) {
            SystemClock.sleep(700); s.onActivity(a->{ assertVisible(a.findViewById(R.id.btnQuiz)); assertVisible(a.findViewById(R.id.wordGrid)); });
            UiEvidence.capture("A2_checked");
        }
        Intent cards=new Intent(ApplicationProvider.getApplicationContext(),FlashcardActivity.class).putExtra("mode","BROWSE")
            .putStringArrayListExtra("wordIds",new ArrayList<>(List.of("mao","gou","niao")));
        try(ActivityScenario<FlashcardActivity> s=ActivityScenario.launch(cards)) {
            SystemClock.sleep(700);
            s.onActivity(a->{
                View listen=a.findViewById(R.id.btnListen);
                listen.requestRectangleOnScreen(new Rect(0,0,listen.getWidth(),listen.getHeight()),true);
            });
            SystemClock.sleep(150);
            s.onActivity(a->{ assertVisible(a.findViewById(R.id.btnNext)); assertVisible(a.findViewById(R.id.btnListen)); });
            UiEvidence.capture("A3_checked");
        }
        Intent quiz=new Intent(ApplicationProvider.getApplicationContext(),QuizActivity.class).putExtra("mode","THEME").putExtra("sessionId",901L);
        try(ActivityScenario<QuizActivity> s=ActivityScenario.launch(quiz)) {
            SystemClock.sleep(700); UiEvidence.capture("A4_unanswered");
            onView(withId(R.id.option0)).perform(click()); SystemClock.sleep(500);
            s.onActivity(a->{
                assertVisible(a.findViewById(R.id.btnNext));
                for(int id:new int[]{R.id.option0,R.id.option1,R.id.option2,R.id.option3}) {
                    View option=a.findViewById(id); assertVisible(option);
                    TextView hanzi=option.findViewById(R.id.optionHanzi);
                    TextView pinyin=option.findViewById(R.id.optionPinyin);
                    assertTrue("option must fit glyph + pinyin plus shadow", option.getHeight()-option.getPaddingBottom()>=hanzi.getHeight()+pinyin.getHeight());
                    assertTextFits(hanzi); assertTextFits(pinyin);
                }
                assertTextFits(a.findViewById(R.id.feedbackText));
            });
            UiEvidence.capture("A4_answered");
        }
    }
    @Test public void quizNewViewModel_restoresOptionsAndAnswer() throws Exception {
        CountDownLatch done=new CountDownLatch(1);
        final Throwable[] error={null};
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                SavedStateHandle handle=new SavedStateHandle();
                QuizViewModel first=new QuizViewModel(c,handle); assertTrue(first.init(QuizViewModel.Mode.THEME,901));
                List<String> options=first.state().getValue().question.options(); first.choose(0);
                c.store.read(s->true, v->{
                    try { QuizViewModel restored=new QuizViewModel(c,new SavedStateHandle());
                        assertTrue(restored.init(QuizViewModel.Mode.THEME,901));
                        assertEquals(options,restored.state().getValue().question.options());
                        assertTrue(restored.state().getValue().answered); assertEquals(0,restored.state().getValue().chosenIndex);
                    } catch(Throwable t){error[0]=t;} finally {done.countDown();}
                });
            } catch(Throwable t){error[0]=t;done.countDown();}
        });
        assertTrue(done.await(10,TimeUnit.SECONDS)); if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private static void assertTextFits(TextView v) {
        assertNotNull(v.getLayout());
        assertTrue("text clipped: "+v.getResources().getResourceEntryName(v.getId()),
            v.getLayout().getHeight()<=v.getHeight()-v.getCompoundPaddingTop()-v.getCompoundPaddingBottom());
    }
    private static void assertVisible(View v) {
        assertNotNull(v); Rect r=new Rect(); assertTrue("not visible: "+v.getId(),v.getGlobalVisibleRect(r));
        assertTrue("clipped vertically: "+v.getId(),r.height()>=v.getHeight()-2);
        assertTrue("too short: "+v.getId(),v.getHeight()>=48*v.getResources().getDisplayMetrics().density);
    }
}
