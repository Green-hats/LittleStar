package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.feature.timeup.TimeUpActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

/** REST / LIMIT 两态渲染。 */
@RunWith(AndroidJUnit4.class)
public final class TimeUpActivityTest {

    @Test
    public void rest_showsCountdown_okDisabled() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), TimeUpActivity.class);
        intent.putExtra("kind", "REST");
        try (ActivityScenario<TimeUpActivity> s = ActivityScenario.launch(intent)) {
            onView(withId(R.id.title)).check(matches(withText(R.string.timeup_rest_title)));
            onView(withId(R.id.countdown)).check(matches(isDisplayed()));
            onView(withId(R.id.btnOk)).check(matches(not(isEnabled())));
            onView(withId(R.id.parentEntry)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void limit_noCountdown_okEnabled_parentEntryVisible() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), TimeUpActivity.class);
        intent.putExtra("kind", "LIMIT");
        try (ActivityScenario<TimeUpActivity> s = ActivityScenario.launch(intent)) {
            onView(withId(R.id.title)).check(matches(withText(R.string.timeup_limit_title)));
            onView(withId(R.id.countdown)).check(matches(not(isDisplayed())));
            onView(withId(R.id.btnOk)).check(matches(isEnabled()));
            onView(withId(R.id.parentEntry)).check(matches(isDisplayed()));
        }
    }
}
