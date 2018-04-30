package org.briarproject.briar.android.journey;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import org.briarproject.briar.android.util.ObjectSerializer;
import org.briarproject.briar.android.util.Prefs;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;

public class JourneyTimer {
    private static HashMap<Calendar, PendingIntent> journeys;
    private static AlarmManager alarmMgr;


    public static void loadSavedJourneys(Context context) {
        if (null == journeys) {
            journeys = new HashMap<>();
        }

        // load tasks from preference
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

        try {
            journeys = (HashMap<Calendar, PendingIntent>) ObjectSerializer.deserialize(pref.getString(Prefs.JOURNEYS, ObjectSerializer.serialize(new HashMap<Calendar, PendingIntent>())));
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (Calendar journey : journeys.keySet()) {
            addJourney(journey, context);
        }
    }

    public static void saveJourneys(Context context) {
        if (null == journeys) {
            journeys = new HashMap<>();
        }

        // save the task list to preference
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        try {
            editor.putString(Prefs.JOURNEYS, ObjectSerializer.serialize(journeys));
        } catch (IOException e) {
            e.printStackTrace();
        }
        editor.commit();
    }

    public static void addJourney(Calendar date, Context context) {
        Log.d("Date: ", date.toString());
        PendingIntent alarmIntent = startJourney(date, context);
        Log.d("Date: ", alarmIntent.toString());
        journeys.put(date, alarmIntent);

    }

    public static PendingIntent startJourney(Calendar date, Context context) {
        Log.i("d********************: ", "aaaaaaaaaaaaaaaaaaa"+date.toString() + " " + date.getTime());
        long time = date.getTimeInMillis() - System.currentTimeMillis() + SystemClock.elapsedRealtime();
        alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("com.sanctuary.emergency");
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        Log.i("d********************: ", "aaaaaaaaaaaaaaaaaaa" + alarmIntent.toString());
        alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, time, alarmIntent);
        Log.i("d********************: ", "aaaaaaaaaaaaaaaaaaa" + time + " " + alarmMgr.toString());
        return alarmIntent;
    }

    public static void stopJourney(Calendar date) {
        if (alarmMgr != null) {
            alarmMgr.cancel(journeys.get(date));
        }
        journeys.remove(date);
    }
}
