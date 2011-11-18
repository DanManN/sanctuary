package net.sf.briar;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import junit.framework.TestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionUpdateWriter;
import net.sf.briar.api.protocol.writers.TransportUpdateWriter;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionContextFactory;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.protocol.writers.ProtocolWritersModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;
import net.sf.briar.transport.batch.TransportBatchModule;
import net.sf.briar.transport.stream.TransportStreamModule;

import org.bouncycastle.util.Arrays;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class ProtocolIntegrationTest extends TestCase {

	private final BatchId ack = new BatchId(TestUtils.getRandomId());
	private final long timestamp = System.currentTimeMillis();

	private final ConnectionContextFactory connectionContextFactory;
	private final ConnectionReaderFactory connectionReaderFactory;
	private final ConnectionWriterFactory connectionWriterFactory;
	private final ProtocolReaderFactory protocolReaderFactory;
	private final ProtocolWriterFactory protocolWriterFactory;
	private final CryptoComponent crypto;
	private final byte[] secret;
	private final ContactId contactId = new ContactId(13);
	private final TransportIndex transportIndex = new TransportIndex(13);
	private final long connection = 12345L;
	private final Author author;
	private final Group group, group1;
	private final Message message, message1, message2, message3;
	private final String authorName = "Alice";
	private final String subject = "Hello";
	private final String messageBody = "Hello world";
	private final Collection<Transport> transports;

	public ProtocolIntegrationTest() throws Exception {
		super();
		Module testModule = new AbstractModule() {
			@Override
			public void configure() {
				bind(Executor.class).toInstance(
						new ScheduledThreadPoolExecutor(5));
			}
		};
		Injector i = Guice.createInjector(testModule, new CryptoModule(),
				new DatabaseModule(), new ProtocolModule(),
				new ProtocolWritersModule(), new SerialModule(),
				new TestDatabaseModule(), new TransportBatchModule(),
				new TransportModule(), new TransportStreamModule());
		connectionContextFactory =
			i.getInstance(ConnectionContextFactory.class);
		connectionReaderFactory = i.getInstance(ConnectionReaderFactory.class);
		connectionWriterFactory = i.getInstance(ConnectionWriterFactory.class);
		protocolReaderFactory = i.getInstance(ProtocolReaderFactory.class);
		protocolWriterFactory = i.getInstance(ProtocolWriterFactory.class);
		crypto = i.getInstance(CryptoComponent.class);
		// Create a shared secret
		Random r = new Random();
		secret = new byte[32];
		r.nextBytes(secret);
		// Create two groups: one restricted, one unrestricted
		GroupFactory groupFactory = i.getInstance(GroupFactory.class);
		group = groupFactory.createGroup("Unrestricted group", null);
		KeyPair groupKeyPair = crypto.generateKeyPair();
		group1 = groupFactory.createGroup("Restricted group",
				groupKeyPair.getPublic().getEncoded());
		// Create an author
		AuthorFactory authorFactory = i.getInstance(AuthorFactory.class);
		KeyPair authorKeyPair = crypto.generateKeyPair();
		author = authorFactory.createAuthor(authorName,
				authorKeyPair.getPublic().getEncoded());
		// Create two messages to each group: one anonymous, one pseudonymous
		MessageFactory messageFactory = i.getInstance(MessageFactory.class);
		message = messageFactory.createMessage(null, group, subject,
				messageBody.getBytes("UTF-8"));
		message1 = messageFactory.createMessage(null, group1,
				groupKeyPair.getPrivate(), subject,
				messageBody.getBytes("UTF-8"));
		message2 = messageFactory.createMessage(null, group, author,
				authorKeyPair.getPrivate(), subject,
				messageBody.getBytes("UTF-8"));
		message3 = messageFactory.createMessage(null, group1,
				groupKeyPair.getPrivate(), author, authorKeyPair.getPrivate(),
				subject, messageBody.getBytes("UTF-8"));
		// Create some transports
		TransportId transportId = new TransportId(TestUtils.getRandomId());
		Transport transport = new Transport(transportId, transportIndex,
				Collections.singletonMap("bar", "baz"));
		transports = Collections.singletonList(transport);
	}

	@Test
	public void testWriteAndRead() throws Exception {
		read(write());
	}

	private byte[] write() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionContext ctx =
			connectionContextFactory.createConnectionContext(contactId,
					transportIndex, connection, Arrays.clone(secret));
		ConnectionWriter w = connectionWriterFactory.createConnectionWriter(out,
				Long.MAX_VALUE, ctx);
		OutputStream out1 = w.getOutputStream();

		AckWriter a = protocolWriterFactory.createAckWriter(out1);
		assertTrue(a.writeBatchId(ack));
		a.finish();

		BatchWriter b = protocolWriterFactory.createBatchWriter(out1);
		assertTrue(b.writeMessage(message.getSerialised()));
		assertTrue(b.writeMessage(message1.getSerialised()));
		assertTrue(b.writeMessage(message2.getSerialised()));
		assertTrue(b.writeMessage(message3.getSerialised()));
		b.finish();

		OfferWriter o = protocolWriterFactory.createOfferWriter(out1);
		assertTrue(o.writeMessageId(message.getId()));
		assertTrue(o.writeMessageId(message1.getId()));
		assertTrue(o.writeMessageId(message2.getId()));
		assertTrue(o.writeMessageId(message3.getId()));
		o.finish();

		RequestWriter r = protocolWriterFactory.createRequestWriter(out1);
		BitSet requested = new BitSet(4);
		requested.set(1);
		requested.set(3);
		r.writeRequest(requested, 4);

		SubscriptionUpdateWriter s =
			protocolWriterFactory.createSubscriptionUpdateWriter(out1);
		// Use a LinkedHashMap for predictable iteration order
		Map<Group, Long> subs = new LinkedHashMap<Group, Long>();
		subs.put(group, 0L);
		subs.put(group1, 0L);
		s.writeSubscriptions(subs, timestamp);

		TransportUpdateWriter t =
			protocolWriterFactory.createTransportUpdateWriter(out1);
		t.writeTransports(transports, timestamp);

		out1.close();
		return out.toByteArray();
	}

	private void read(byte[] connectionData) throws Exception {
		InputStream in = new ByteArrayInputStream(connectionData);
		byte[] encryptedIv = new byte[16];
		assertEquals(16, in.read(encryptedIv, 0, 16));
		ConnectionContext ctx =
			connectionContextFactory.createConnectionContext(contactId,
					transportIndex, connection, Arrays.clone(secret));
		ConnectionReader r = connectionReaderFactory.createConnectionReader(in,
				ctx, encryptedIv);
		in = r.getInputStream();
		ProtocolReader protocolReader =
			protocolReaderFactory.createProtocolReader(in);

		// Read the ack
		assertTrue(protocolReader.hasAck());
		Ack a = protocolReader.readAck();
		assertEquals(Collections.singletonList(ack), a.getBatchIds());

		// Read the batch
		assertTrue(protocolReader.hasBatch());
		Batch b = protocolReader.readBatch();
		Collection<Message> messages = b.getMessages();
		assertEquals(4, messages.size());
		Iterator<Message> it = messages.iterator();
		checkMessageEquality(message, it.next());
		checkMessageEquality(message1, it.next());
		checkMessageEquality(message2, it.next());
		checkMessageEquality(message3, it.next());

		// Read the offer
		assertTrue(protocolReader.hasOffer());
		Offer o = protocolReader.readOffer();
		Collection<MessageId> offered = o.getMessageIds();
		assertEquals(4, offered.size());
		Iterator<MessageId> it1 = offered.iterator();
		assertEquals(message.getId(), it1.next());
		assertEquals(message1.getId(), it1.next());
		assertEquals(message2.getId(), it1.next());
		assertEquals(message3.getId(), it1.next());

		// Read the request
		assertTrue(protocolReader.hasRequest());
		Request req = protocolReader.readRequest();
		BitSet requested = req.getBitmap();
		assertFalse(requested.get(0));
		assertTrue(requested.get(1));
		assertFalse(requested.get(2));
		assertTrue(requested.get(3));
		// If there are any padding bits, they should all be zero
		assertEquals(2, requested.cardinality());

		// Read the subscription update
		assertTrue(protocolReader.hasSubscriptionUpdate());
		SubscriptionUpdate s = protocolReader.readSubscriptionUpdate();
		Map<Group, Long> subs = s.getSubscriptions();
		assertEquals(2, subs.size());
		assertEquals(Long.valueOf(0L), subs.get(group));
		assertEquals(Long.valueOf(0L), subs.get(group1));
		assertTrue(s.getTimestamp() == timestamp);

		// Read the transport update
		assertTrue(protocolReader.hasTransportUpdate());
		TransportUpdate t = protocolReader.readTransportUpdate();
		assertEquals(transports, t.getTransports());
		assertTrue(t.getTimestamp() == timestamp);

		in.close();
	}

	private void checkMessageEquality(Message m1, Message m2) {
		assertEquals(m1.getId(), m2.getId());
		assertEquals(m1.getParent(), m2.getParent());
		assertEquals(m1.getGroup(), m2.getGroup());
		assertEquals(m1.getAuthor(), m2.getAuthor());
		assertEquals(m1.getTimestamp(), m2.getTimestamp());
		assertArrayEquals(m1.getSerialised(), m2.getSerialised());
	}
}
