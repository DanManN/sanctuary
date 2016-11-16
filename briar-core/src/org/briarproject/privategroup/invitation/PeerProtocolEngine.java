package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.ProtocolStateException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.privategroup.invitation.PeerState.AWAIT_MEMBER;
import static org.briarproject.privategroup.invitation.PeerState.BOTH_JOINED;
import static org.briarproject.privategroup.invitation.PeerState.ERROR;
import static org.briarproject.privategroup.invitation.PeerState.LOCAL_JOINED;
import static org.briarproject.privategroup.invitation.PeerState.LOCAL_LEFT;
import static org.briarproject.privategroup.invitation.PeerState.NEITHER_JOINED;
import static org.briarproject.privategroup.invitation.PeerState.START;

@Immutable
@NotNullByDefault
class PeerProtocolEngine extends AbstractProtocolEngine<PeerSession> {

	PeerProtocolEngine(DatabaseComponent db, ClientHelper clientHelper,
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
	public PeerSession onInviteAction(Transaction txn, PeerSession s,
			@Nullable String message, long timestamp, byte[] signature)
			throws DbException {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	@Override
	public PeerSession onJoinAction(Transaction txn, PeerSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
			case AWAIT_MEMBER:
			case LOCAL_JOINED:
			case BOTH_JOINED:
			case ERROR:
				throw new ProtocolStateException(); // Invalid in these states
			case NEITHER_JOINED:
				return onLocalJoinFromNeitherJoined(txn, s);
			case LOCAL_LEFT:
				return onLocalJoinFromLocalLeft(txn, s);
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onLeaveAction(Transaction txn, PeerSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
			case AWAIT_MEMBER:
			case NEITHER_JOINED:
			case LOCAL_LEFT:
			case ERROR:
				return s; // Ignored in these states
			case LOCAL_JOINED:
				return onLocalLeaveFromLocalJoined(txn, s);
			case BOTH_JOINED:
				return onLocalLeaveFromBothJoined(txn, s);
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onMemberAddedAction(Transaction txn, PeerSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
				return onMemberAddedFromStart(s);
			case AWAIT_MEMBER:
				return onMemberAddedFromAwaitMember(txn, s);
			case NEITHER_JOINED:
			case LOCAL_JOINED:
			case BOTH_JOINED:
			case LOCAL_LEFT:
				throw new ProtocolStateException(); // Invalid in these states
			case ERROR:
				return s; // Ignored in this state
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onInviteMessage(Transaction txn, PeerSession s,
			InviteMessage m) throws DbException, FormatException {
		return abort(txn, s); // Invalid in this role
	}

	@Override
	public PeerSession onJoinMessage(Transaction txn, PeerSession s,
			JoinMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case AWAIT_MEMBER:
			case BOTH_JOINED:
			case LOCAL_LEFT:
				return abort(txn, s); // Invalid in these states
			case START:
				return onRemoteJoinFromStart(txn, s, m);
			case NEITHER_JOINED:
				return onRemoteJoinFromNeitherJoined(txn, s, m);
			case LOCAL_JOINED:
				return onRemoteJoinFromLocalJoined(txn, s, m);
			case ERROR:
				return s; // Ignored in this state
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onLeaveMessage(Transaction txn, PeerSession s,
			LeaveMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case START:
			case NEITHER_JOINED:
			case LOCAL_JOINED:
				return abort(txn, s); // Invalid in these states
			case AWAIT_MEMBER:
				return onRemoteLeaveFromAwaitMember(txn, s, m);
			case LOCAL_LEFT:
				return onRemoteLeaveFromLocalLeft(txn, s, m);
			case BOTH_JOINED:
				return onRemoteLeaveFromBothJoined(txn, s, m);
			case ERROR:
				return s; // Ignored in this state
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onAbortMessage(Transaction txn, PeerSession s,
			AbortMessage m) throws DbException, FormatException {
		return abort(txn, s);
	}

	private PeerSession onLocalJoinFromNeitherJoined(Transaction txn,
			PeerSession s) throws DbException {
		// Send a JOIN message
		Message sent = sendJoinMessage(txn, s, false);
		// Move to the LOCAL_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				LOCAL_JOINED);
	}

	private PeerSession onLocalJoinFromLocalLeft(Transaction txn, PeerSession s)
			throws DbException {
		// Send a JOIN message
		Message sent = sendJoinMessage(txn, s, false);
		try {
			// Start syncing the private group with the contact
			syncPrivateGroupWithContact(txn, s, true);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// The relationship is already marked visible to the group
		// Move to the BOTH_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				BOTH_JOINED);
	}

	private PeerSession onLocalLeaveFromBothJoined(Transaction txn,
			PeerSession s) throws DbException {
		// Send a LEAVE message
		Message sent = sendLeaveMessage(txn, s, false);
		try {
			// Stop syncing the private group with the contact
			syncPrivateGroupWithContact(txn, s, false);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Move to the LOCAL_LEFT state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				LOCAL_LEFT);
	}

	private PeerSession onLocalLeaveFromLocalJoined(Transaction txn,
			PeerSession s) throws DbException {
		// Send a LEAVE message
		Message sent = sendLeaveMessage(txn, s, false);
		// Move to the NEITHER_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				NEITHER_JOINED);
	}

	private PeerSession onMemberAddedFromStart(PeerSession s) {
		// Move to the NEITHER_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), s.getLastRemoteMessageId(),
				s.getLocalTimestamp(), NEITHER_JOINED);
	}

	private PeerSession onMemberAddedFromAwaitMember(Transaction txn,
			PeerSession s) throws DbException {
		// Send a JOIN message
		Message sent = sendJoinMessage(txn, s, false);
		try {
			// Start syncing the private group with the contact
			syncPrivateGroupWithContact(txn, s, true);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		try {
			// Mark the relationship visible to the group, revealed by contact
			relationshipRevealed(txn, s, true);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Move to the BOTH_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				BOTH_JOINED);
	}

	private PeerSession onRemoteJoinFromStart(Transaction txn,
			PeerSession s, JoinMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Move to the AWAIT_MEMBER state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				AWAIT_MEMBER);
	}

	private PeerSession onRemoteJoinFromNeitherJoined(Transaction txn,
			PeerSession s, JoinMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Send a JOIN message
		Message sent = sendJoinMessage(txn, s, false);
		// Start syncing the private group with the contact
		syncPrivateGroupWithContact(txn, s, true);
		// Mark the relationship visible to the group, revealed by contact
		relationshipRevealed(txn, s, true);
		// Move to the BOTH_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), m.getId(), sent.getTimestamp(), BOTH_JOINED);
	}

	private PeerSession onRemoteJoinFromLocalJoined(Transaction txn,
			PeerSession s, JoinMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Start syncing the private group with the contact
		syncPrivateGroupWithContact(txn, s, true);
		// Mark the relationship visible to the group, revealed by us
		relationshipRevealed(txn, s, false);
		// Move to the BOTH_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				BOTH_JOINED);
	}

	private PeerSession onRemoteLeaveFromAwaitMember(Transaction txn,
			PeerSession s, LeaveMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Move to the START state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				START);
	}

	private PeerSession onRemoteLeaveFromLocalLeft(Transaction txn,
			PeerSession s, LeaveMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Move to the NEITHER_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				NEITHER_JOINED);
	}

	private PeerSession onRemoteLeaveFromBothJoined(Transaction txn,
			PeerSession s, LeaveMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Stop syncing the private group with the contact
		syncPrivateGroupWithContact(txn, s, false);
		// Move to the LOCAL_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				LOCAL_JOINED);
	}

	private PeerSession abort(Transaction txn, PeerSession s)
			throws DbException, FormatException {
		// If the session has already been aborted, do nothing
		if (s.getState() == ERROR) return s;
		// Stop syncing the private group with the contact, if we subscribe
		if (isSubscribedPrivateGroup(txn, s.getPrivateGroupId()))
			syncPrivateGroupWithContact(txn, s, false);
		// Send an ABORT message
		Message sent = sendAbortMessage(txn, s);
		// Move to the ERROR state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				ERROR);
	}

	private void relationshipRevealed(Transaction txn, PeerSession s,
			boolean byContact) throws DbException, FormatException {
		ContactId contactId = getContactId(txn, s.getContactGroupId());
		Contact contact = db.getContact(txn, contactId);
		privateGroupManager.relationshipRevealed(txn, s.getPrivateGroupId(),
				contact.getAuthor().getId(), byContact);
	}
}