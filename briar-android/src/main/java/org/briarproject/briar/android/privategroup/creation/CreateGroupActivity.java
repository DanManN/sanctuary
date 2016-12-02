package org.briarproject.briar.android.privategroup.creation;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.privategroup.conversation.GroupActivity;
import org.briarproject.briar.android.sharing.BaseMessageFragment.MessageFragmentListener;

import javax.annotation.Nullable;

import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CreateGroupActivity extends BaseGroupInviteActivity implements
		CreateGroupListener, MessageFragmentListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle bundle) {
		super.onCreate(bundle);

		if (bundle == null) {
			CreateGroupFragment fragment = new CreateGroupFragment();
			getSupportFragmentManager().beginTransaction()
					.add(R.id.fragmentContainer, fragment)
					.commit();
		}
	}

	@Override
	public void onBackPressed() {
		if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
			// At this point, the group had been created already,
			// so don't allow to create it again.
			openNewGroup();
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void onGroupNameChosen(String name) {
		controller.createGroup(name,
				new UiResultExceptionHandler<GroupId, DbException>(this) {
					@Override
					public void onResultUi(GroupId g) {
						groupId = g;
						switchToContactSelectorFragment(g);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
	}

	private void switchToContactSelectorFragment(GroupId g) {
		setTitle(R.string.groups_invite_members);
		GroupInviteFragment fragment =
				GroupInviteFragment.newInstance(g);
		getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(android.R.anim.fade_in,
						android.R.anim.fade_out,
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right)
				.replace(R.id.fragmentContainer, fragment)
				.addToBackStack(fragment.getUniqueTag())
				.commit();
	}

	private void openNewGroup() {
		Intent i = new Intent(this, GroupActivity.class);
		i.putExtra(GROUP_ID, groupId.getBytes());
		ActivityOptionsCompat options =
				makeCustomAnimation(this, android.R.anim.fade_in,
						android.R.anim.fade_out);
		ActivityCompat.startActivity(this, i, options.toBundle());
		// finish this activity, so we can't come back to it
		finish();
	}

}