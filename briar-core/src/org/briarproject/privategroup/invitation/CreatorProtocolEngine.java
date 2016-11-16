package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.ProtocolStateException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.GroupInvitationResponseReceivedEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.privategroup.invitation.GroupInvitationResponse;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.privategroup.invitation.CreatorState.DISSOLVED;
import static org.briarproject.privategroup.invitation.CreatorState.ERROR;
import static org.briarproject.privategroup.invitation.CreatorState.INVITED;
import static org.briarproject.privategroup.invitation.CreatorState.INVITEE_JOINED;
import static org.briarproject.privategroup.invitation.CreatorState.INVITEE_LEFT;
import static org.briarproject.privategroup.invitation.CreatorState.START;

@Immutable
@NotNullByDefault
class CreatorProtocolEngine extends AbstractProtocolEngine<CreatorSession> {

	CreatorProtocolEngine(DatabaseComponent db, ClientHelper clientHelper,
			PrivateGroupManager privateGroupManager,
			PrivateGroupFactory privateGroupFactory,
			GroupMessageFactory groupMessageFactory,
			IdentityManager identityManager, MessageParser messageParser,
			MessageEncoder messageEncoder, MessageTracker messageTracker,
			Clock clock) {
		super(db, clientHelper, privateGroupManager, privateGroupFactory,
				groupMessageFactory, identityManager, messageParser,
				messageEncoder, messageTracker, clock);
	}

	@Override
	public CreatorSession onInviteAction(Transaction txn, CreatorSession s,
			@Nullable String message, long timestamp, byte[] signature)
			throws DbException {
		switch (s.getState()) {
			case START:
				return onLocalInvite(txn, s, message, timestamp, signature);
			case INVITED:
			case INVITEE_JOINED:
			case INVITEE_LEFT:
			case DISSOLVED:
			case ERROR:
				throw new ProtocolStateException(); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public CreatorSession onJoinAction(Transaction txn, CreatorSession s)
			throws DbException {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	@Override
	public CreatorSession onLeaveAction(Transaction txn, CreatorSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
			case DISSOLVED:
			case ERROR:
				return s; // Ignored in these states
			case INVITED:
			case INVITEE_JOINED:
			case INVITEE_LEFT:
				return onLocalLeave(txn, s);
			default:
				throw new AssertionError();
		}
	}

	@Override
	public CreatorSession onMemberAddedAction(Transaction txn, CreatorSession s)
			throws DbException {
		return s; // Ignored in this role
	}

	@Override
	public CreatorSession onInviteMessage(Transaction txn, CreatorSession s,
			InviteMessage m) throws DbException, FormatException {
		return abort(txn, s); // Invalid in this role
	}

	@Override
	public CreatorSession onJoinMessage(Transaction txn, CreatorSession s,
			JoinMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case START:
			case INVITEE_JOINED:
			case INVITEE_LEFT:
				return abort(txn, s); // Invalid in these states
			case INVITED:
				return onRemoteAccept(txn, s, m);
			case DISSOLVED:
			case ERROR:
				return s; // Ignored in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public CreatorSession onLeaveMessage(Transaction txn, CreatorSession s,
			LeaveMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case START:
			case INVITEE_LEFT:
				return abort(txn, s); // Invalid in these states
			case INVITED:
				return onRemoteDecline(txn, s, m);
			case INVITEE_JOINED:
				return onRemoteLeave(txn, s, m);
			case DISSOLVED:
			case ERROR:
				return s; // Ignored in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public CreatorSession onAbortMessage(Transaction txn, CreatorSession s,
			AbortMessage m) throws DbException, FormatException {
		return abort(txn, s);
	}

	private CreatorSession onLocalInvite(Transaction txn, CreatorSession s,
			@Nullable String message, long timestamp, byte[] signature)
			throws DbException {
		// Send an INVITE message
		Message sent = sendInviteMessage(txn, s, message, timestamp, signature);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);
		// Move to the INVITED state
		long localTimestamp = Math.max(timestamp, getLocalTimestamp(s));
		return new CreatorSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), localTimestamp,
				timestamp, INVITED);
	}

	private CreatorSession onLocalLeave(Transaction txn, CreatorSession s)
			throws DbException {
		try {
			// Stop syncing the private group with the contact
			syncPrivateGroupWithContact(txn, s, false);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Send a LEAVE message
		Message sent = sendLeaveMessage(txn, s, false);
		// Move to the DISSOLVED state
		return new CreatorSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp(), DISSOLVED);
	}

	private CreatorSession onRemoteAccept(Transaction txn, CreatorSession s,
			JoinMessage m) throws DbException, FormatException {
		// The timestamp must be higher than the last invite message
		if (m.getTimestamp() <= s.getInviteTimestamp()) return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getId(), true);
		// Track the message
		messageTracker.trackMessage(txn, m.getContactGroupId(),
				m.getTimestamp(), false);
		// Start syncing the private group with the contact
		syncPrivateGroupWithContact(txn, s, true);
		// Broadcast an event
		ContactId contactId = getContactId(txn, m.getContactGroupId());
		txn.attach(new GroupInvitationResponseReceivedEvent(contactId,
				createInvitationResponse(m, contactId, true)));
		// Move to the INVITEE_JOINED state
		return new CreatorSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp(), INVITEE_JOINED);
	}

	private CreatorSession onRemoteDecline(Transaction txn, CreatorSession s,
			LeaveMessage m) throws DbException, FormatException {
		// The timestamp must be higher than the last invite message
		if (m.getTimestamp() <= s.getInviteTimestamp()) return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getId(), true);
		// Track the message
		messageTracker.trackMessage(txn, m.getContactGroupId(),
				m.getTimestamp(), false);
		// Broadcast an event
		ContactId contactId = getContactId(txn, m.getContactGroupId());
		txn.attach(new GroupInvitationResponseReceivedEvent(contactId,
				createInvitationResponse(m, contactId, false)));
		// Move to the START state
		return new CreatorSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp(), START);
	}

	private CreatorSession onRemoteLeave(Transaction txn, CreatorSession s,
			LeaveMessage m) throws DbException, FormatException {
		// The timestamp must be higher than the last invite message
		if (m.getTimestamp() <= s.getInviteTimestamp()) return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Stop syncing the private group with the contact
		syncPrivateGroupWithContact(txn, s, false);
		// Move to the INVITEE_LEFT state
		return new CreatorSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp(), INVITEE_LEFT);
	}

	private CreatorSession abort(Transaction txn, CreatorSession s)
			throws DbException, FormatException {
		// If the session has already been aborted, do nothing
		if (s.getState() == ERROR) return s;
		// If we subscribe, stop syncing the private group with the contact
		if (isSubscribedPrivateGroup(txn, s.getPrivateGroupId()))
			syncPrivateGroupWithContact(txn, s, false);
		// Send an ABORT message
		Message sent = sendAbortMessage(txn, s);
		// Move to the ERROR state
		return new CreatorSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp(), ERROR);
	}

	private GroupInvitationResponse createInvitationResponse(
			GroupInvitationMessage m, ContactId c, boolean accept) {
		SessionId sessionId = new SessionId(m.getPrivateGroupId().getBytes());
		return new GroupInvitationResponse(m.getId(), sessionId,
				m.getContactGroupId(), c, accept, m.getTimestamp(), false,
				false, true, false);
	}
}