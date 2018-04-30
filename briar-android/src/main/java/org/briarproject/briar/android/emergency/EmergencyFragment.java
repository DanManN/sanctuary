package org.briarproject.briar.android.emergency;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseEventFragment;
import org.briarproject.briar.api.android.AndroidNotificationManager;

import java.util.Calendar;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class EmergencyFragment extends BaseEventFragment implements OnClickListener{

    public final static String TAG = EmergencyFragment.class.getName();
    private final static Logger LOG = Logger.getLogger(TAG);

    @Inject
    AndroidNotificationManager notificationManager;

    public static EmergencyFragment newInstance() {

        Bundle args = new Bundle();

        EmergencyFragment fragment = new EmergencyFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        getActivity().setTitle(R.string.emergency_button);

        View contentView =
                inflater.inflate(R.layout.emergency, container,
                        false);
        Button eswitch = contentView.findViewById(R.id.emergency_switch);
        eswitch.setOnClickListener(this);
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
        //inflater.inflate(R.menu.journey_actions, menu);
        //super.onCreateOptionsMenu(menu, inflater);
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
        switch (view.getId()) {
            case R.id.emergency_switch:
                Log.i("MESSAGE: ", "aaaaaaaaaaa: " + "ALERT ALL");
                break;
        }

    }

}
