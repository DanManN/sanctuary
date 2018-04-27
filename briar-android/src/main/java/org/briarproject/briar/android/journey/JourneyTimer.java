package org.briarproject.briar.android.journey;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.briar.android.util.ObjectSerializer;
import org.briarproject.briar.android.util.Prefs;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

public class JourneyTimer {
    private static HashMap<Date, PendingIntent> journeys;
    private static AlarmManager alarmMgr;


    public static void loadSavedJourneys(Context context) {
        if (null == journeys) {
            journeys = new HashMap<>();
        }

        // load tasks from preference
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

        try {
            journeys = (HashMap<Date, PendingIntent>) ObjectSerializer.deserialize(pref.getString(Prefs.JOURNEYS, ObjectSerializer.serialize(new HashMap<Date, PendingIntent>())));
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (Date journey : journeys.keySet()) {
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

    public static void addJourney(Date date, Context context) {
        Log.d("Date: ", date.toString());
        PendingIntent alarmIntent = startJourney(date, context);
        Log.d("Date: ", alarmIntent.toString());
        journeys.put(date, startJourney(date, context));
    }

    public static PendingIntent startJourney(Date date, Context context) {
        long time = date.getTime() - (new Date()).getTime();
        alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, JourneyFragment.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, time, alarmIntent);
        return alarmIntent;
    }

    public static void stopJourney(Date date) {
        if (alarmMgr != null) {
            alarmMgr.cancel(journeys.get(date));
        }
        journeys.remove(date);
    }
}
