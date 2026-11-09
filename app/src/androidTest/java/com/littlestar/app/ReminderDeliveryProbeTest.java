package com.littlestar.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Schedules the actual AlarmManager path; never posts a notification directly. */
@RunWith(AndroidJUnit4.class)
public class ReminderDeliveryProbeTest {
 @Test public void scheduleRealReminderForNextMinute() throws Exception {
  Context context=ApplicationProvider.getApplicationContext();
  assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS));
  android.app.NotificationManager nm=context.getSystemService(android.app.NotificationManager.class);
  nm.cancel(com.littlestar.app.notify.Notifier.ID_REMINDER);
  nm.cancel(com.littlestar.app.notify.Notifier.ID_TEST);
  AppContainer c=((LittleStarApp)context).container();
  ZonedDateTime target=ZonedDateTime.now(c.clock.zone()).plusMinutes(2).withSecond(0).withNano(0);
  c.settings.setReminderTime(String.format(Locale.ROOT,"%02d:%02d",target.getHour(),target.getMinute()));
  c.settings.setReminderDays(Set.of(target.getDayOfWeek().getValue()));
  c.notifier.ensureChannels();c.reminders.reschedule();
  long scheduled=c.reminders.nextTriggerMillis();
  assertEquals(target.toInstant().toEpochMilli(),scheduled);
  File dir=context.getExternalFilesDir("team-qa");assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
  String json="{\"trigger_at_millis\":"+scheduled+",\"window_millis\":600000,\"reminder_time\":\""+c.settings.getReminderTime()+"\",\"iso_weekday\":"+target.getDayOfWeek().getValue()+",\"path\":\"ReminderScheduler -> AlarmManager -> ReminderReceiver -> Notifier\"}";
  try(FileOutputStream out=new FileOutputStream(new File(dir,"scheduled_reminder_probe.json"))){out.write(json.getBytes(StandardCharsets.UTF_8));}
 }
}
