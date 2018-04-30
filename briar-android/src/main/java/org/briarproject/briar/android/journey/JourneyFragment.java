package org.briarproject.briar.android.journey;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.TimePicker;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseEventFragment;
import org.briarproject.briar.api.android.AndroidNotificationManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class JourneyFragment extends BaseEventFragment implements OnClickListener {

    public final static String TAG = JourneyFragment.class.getName();
    private final static Logger LOG = Logger.getLogger(TAG);
    private static Date journeyToAdd;

    @Inject
    AndroidNotificationManager notificationManager;

    //@Inject
    //volatile ForumSharingManager forumSharingManager;

    public static JourneyFragment newInstance() {

        Bundle args = new Bundle();

        JourneyFragment fragment = new JourneyFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        getActivity().setTitle(R.string.journey_button);

        View contentView =
                inflater.inflate(R.layout.journey, container,
                        false);

        return contentView;
    }

    @Override
    public String getUniqueTag() {
        return TAG;
    }

    @Override
    public void injectFragment(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        notificationManager.clearAllForumPostNotifications();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.journey_actions, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_create_journey:
                //Intent intent =
                //        new Intent(getContext(), CreateForumActivity.class);
                //startActivity(intent);
                DialogFragment newFragment = new DatePickerFragment();
                newFragment.show(this.getFragmentManager(), "datePicker");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void eventOccurred(Event e) {
		/*if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, reloading available forums");
		} else if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			if (g.getGroup().getClientId().equals(CLIENT_ID)) {
				LOG.info("Forum added, reloading forums");
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getClientId().equals(CLIENT_ID)) {
				LOG.info("Forum removed, removing from list");
				removeForum(g.getGroup().getId());
			}
		} else if (e instanceof ForumPostReceivedEvent) {
			ForumPostReceivedEvent f = (ForumPostReceivedEvent) e;
			LOG.info("Forum post added, updating item");
			updateItem(f.getGroupId(), f.getHeader());
		} else if (e instanceof ForumInvitationRequestReceivedEvent) {
			LOG.info("Forum invitation received, reloading available forums");
		}*/
    }

    @Override
    public void onClick(View view) {
/*//		// snackbar click
//		Intent i = new Intent(getContext(), ForumInvitationActivity.class);
//		startActivity(i);*/
    }

    @Override
    public void onSaveInstanceState(Bundle saveInstanceState) {
        super.onSaveInstanceState(saveInstanceState);
        saveDates();
    }

    private void saveDates() {

    }

    public static class DatePickerFragment extends DialogFragment
                            implements DatePickerDialog.OnDateSetListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current date as the default date in the picker
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            // Do something with the date chosen by the user
            journeyToAdd = new Date(year, month, day);
            DialogFragment newFragment = new TimePickerFragment();
            newFragment.show(this.getFragmentManager(), "timePicker");
        }
    }

    public static class TimePickerFragment extends DialogFragment
                            implements TimePickerDialog.OnTimeSetListener {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current time as the default values for the picker
        final Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);

        // Create a new instance of TimePickerDialog and return it
        return new TimePickerDialog(getActivity(), this, hour, minute,
                DateFormat.is24HourFormat(getActivity()));
    }

    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        // Do something with the time chosen by the user
        journeyToAdd.setHours(hourOfDay);
        journeyToAdd.setMinutes(minute);
        //Log.i("d********************: ", "aaaaaaaaaaaaaaaaaaa"+journeyToAdd.toString());
        JourneyTimer.addJourney(journeyToAdd,getActivity().getApplicationContext());
    }
}
}
