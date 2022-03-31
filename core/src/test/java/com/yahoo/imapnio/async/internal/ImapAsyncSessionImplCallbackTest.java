package com.yahoo.imapnio.async.internal;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.sun.mail.iap.ProtocolException;
import com.sun.mail.imap.protocol.IMAPResponse;
import com.yahoo.imapnio.async.client.ImapAsyncSession.DebugMode;
import com.yahoo.imapnio.async.client.ImapFuture;
import com.yahoo.imapnio.async.data.Capability;
import com.yahoo.imapnio.async.exception.ImapAsyncClientException;
import com.yahoo.imapnio.async.internal.ImapAsyncSessionImpl.ImapChannelClosedListener;
import com.yahoo.imapnio.async.request.AuthPlainCommand;
import com.yahoo.imapnio.async.request.CapaCommand;
import com.yahoo.imapnio.async.request.ImapRequest;
import com.yahoo.imapnio.async.response.ImapAsyncResponse;
import com.yahoo.imapnio.async.response.ImapResponseMapper;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

/**
 * Unit test for {@link ImapAsyncSessionImpl} callbacks.
 */
public class ImapAsyncSessionImplCallbackTest {

    /** Dummy session id. */
    private static final Long SESSION_ID = Long.valueOf(123456);

    /** Dummy user id. */
    private static final String USER_ID = "Argentinosaurus@long.enough";

    /** Timeout in milliseconds for making get on future. */
    private static final long FUTURE_GET_TIMEOUT_MILLIS = 5L;

    /** Time sequence for the clock tick in milliseconds. */
    private static final Long[] TIME_SEQUENCE = { 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L,
            23L, 24L, 25L, 26L, 27L, 28L, 29L, 30L, 31L, 32L, 33L, 34L, 35L, 36L, 37L, 38L, 39L, 40L, 41L, 42L, 43L, 44L, 45L, 46L, 47L, 48L, 49L,
            50L, 51L, 52L, 53L, 54L, 55L, 56L, 57L, 58L, 59L, 60L };

    /** Clock instance. */
    private Clock clock;

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        // Use reflection to get all declared non-primitive non-static fields (We do not care about inherited fields)
        final Class<?> c = ImapAsyncSessionImpl.class;
        /** Fields to check for cleanup. */
        final Set<Field> fieldsToCheck = new HashSet<>();

        for (final Field declaredField : c.getDeclaredFields()) {
            if (!declaredField.getType().isPrimitive() && !Modifier.isStatic(declaredField.getModifiers())) {
                declaredField.setAccessible(true);
                fieldsToCheck.add(declaredField);
            }
        }
    }

    /**
     * Sets up instance before each test method.
     */
    @BeforeMethod
    public void beforeMethod() {
        clock = Mockito.mock(Clock.class);
        Mockito.when(clock.millis()).thenReturn(1L, TIME_SEQUENCE);
    }

    /**
     * Tests that the success callback is called.
     *
     * @throws IOException will not throw
     * @throws ImapAsyncClientException will not throw
     * @throws ProtocolException will not throw
     * @throws TimeoutException will not throw
     * @throws ExecutionException will not throw
     * @throws InterruptedException will not throw
     */
    @Test
    public void testExecuteSuccessCallback()
            throws ImapAsyncClientException, IOException, ProtocolException, InterruptedException, ExecutionException, TimeoutException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise authWritePromise = Mockito.mock(ChannelPromise.class); // first
        final ChannelPromise authWritePromise2 = Mockito.mock(ChannelPromise.class); // after +
        final ChannelPromise capaWritePromise = Mockito.mock(ChannelPromise.class);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(authWritePromise).thenReturn(authWritePromise2).thenReturn(capaWritePromise)
                .thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(false);

        // construct, both class level and session level debugging are off

        final String sessionCtx = USER_ID;
        final ImapAsyncSessionImpl aSession = new ImapAsyncSessionImpl(clock, channel, logger, DebugMode.DEBUG_OFF, SESSION_ID, pipeline, sessionCtx);

        // execute Authenticate plain command
        {
            final Map<String, List<String>> capas = new HashMap<String, List<String>>();
            final ImapRequest cmd = new AuthPlainCommand("orange", "juicy", new Capability(capas));
            final ImapFuture<ImapAsyncResponse> future = aSession.execute(cmd);
            Mockito.verify(authWritePromise, Mockito.times(1)).addListener(Mockito.any(ImapAsyncSessionImpl.class));
            Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
            Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

            // simulate write to server completed successfully
            Mockito.when(authWritePromise.isSuccess()).thenReturn(true);
            aSession.operationComplete(authWritePromise);

            // handle server response
            final IMAPResponse serverResp1 = new IMAPResponse("+");
            // following will call getNextCommandLineAfterContinuation
            aSession.handleChannelResponse(serverResp1);
            Mockito.verify(channel, Mockito.times(2)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));

            final IMAPResponse serverResp2 = new IMAPResponse("a1 OK AUTHENTICATE completed");
            aSession.handleChannelResponse(serverResp2);

            // verify that future should be done now
            Assert.assertTrue(future.isDone(), "isDone() should be true now");
            final ImapAsyncResponse asyncResp = future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            final Collection<IMAPResponse> lines = asyncResp.getResponseLines();
            Assert.assertEquals(lines.size(), 2, "responses count mismatched.");
            final Iterator<IMAPResponse> it = lines.iterator();
            final IMAPResponse continuationResp = it.next();
            Assert.assertNotNull(continuationResp, "Result mismatched.");
            Assert.assertTrue(continuationResp.isContinuation(), "Response.isContinuation() mismatched.");
            final IMAPResponse endingResp = it.next();
            Assert.assertNotNull(endingResp, "Result mismatched.");
            Assert.assertTrue(endingResp.isOK(), "Response.isOK() mismatched.");
            Assert.assertEquals(endingResp.getTag(), "a1", "tag mismatched.");
            // verify no log messages
            Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        }

        {
            // setting debug on for the session
            Mockito.when(logger.isDebugEnabled()).thenReturn(true);
            aSession.setDebugMode(DebugMode.DEBUG_ON);
            // execute capa
            final ImapRequest cmd = new CapaCommand();
            final AtomicReference<ImapAsyncResponse> response = new AtomicReference<>();
            final ImapFuture<ImapAsyncResponse> future = aSession.execute(cmd, new Consumer<ImapAsyncResponse>() {
                @Override
                public void accept(final ImapAsyncResponse imapAsyncResponse) {
                    response.set(imapAsyncResponse);
                }
            }, new Consumer<Exception>() {
                @Override
                public void accept(final Exception e) {

                }
            }, new Runnable() {
                @Override
                public void run() {

                }
            });

            Mockito.verify(capaWritePromise, Mockito.times(1)).addListener(Mockito.any(ImapAsyncSessionImpl.class));
            Mockito.verify(channel, Mockito.times(3)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
            Mockito.verify(logger, Mockito.times(1)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

            // simulate write to server completed successfully
            Mockito.when(capaWritePromise.isSuccess()).thenReturn(true);
            aSession.operationComplete(capaWritePromise);

            // handle server response
            final IMAPResponse serverResp1 = new IMAPResponse(
                    "* CAPABILITY IMAP4rev1 SASL-IR AUTH=PLAIN AUTH=XOAUTH2 AUTH=OAUTHBEARER ID MOVE NAMESPACE");
            aSession.handleChannelResponse(serverResp1);

            final IMAPResponse serverRespJunk = new IMAPResponse("@@@@@* some junk MOVE NAMESPACE");
            aSession.handleChannelResponse(serverRespJunk);
            Assert.assertFalse(future.isDone(), "isDone() should be false.");

            final IMAPResponse anotherTaggedResp = new IMAPResponse("a1 OK but not the tag u sent!");
            aSession.handleChannelResponse(anotherTaggedResp);
            Assert.assertFalse(future.isDone(), "isDone() should be false.");

            final IMAPResponse serverResp2 = new IMAPResponse("a2 OK CAPABILITY completed");
            aSession.handleChannelResponse(serverResp2);

            // verify that future should be done now
            Assert.assertTrue(future.isDone(), "isDone() should be true now");
            final ImapAsyncResponse asyncResp = future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            final Collection<IMAPResponse> lines = asyncResp.getResponseLines();
            Assert.assertEquals(lines.size(), 4, "responses count mismatched.");
            final Iterator<IMAPResponse> it = lines.iterator();
            final IMAPResponse capaResp = it.next();
            Assert.assertNotNull(capaResp, "Result mismatched.");
            Assert.assertFalse(capaResp.isContinuation(), "Response.isContinuation() mismatched.");
            final ImapResponseMapper parser = new ImapResponseMapper();
            final Capability capa = parser.readValue(lines.toArray(new IMAPResponse[0]), Capability.class);
            Assert.assertTrue(capa.hasCapability("IMAP4rev1".toUpperCase()), "One capability missed.");
            Assert.assertTrue(capa.hasCapability("SASL-IR"), "One capability missed.");
            Assert.assertTrue(capa.hasCapability("ID"), "One capability missed.");
            Assert.assertTrue(capa.hasCapability("MOVE"), "One capability missed.");
            Assert.assertTrue(capa.hasCapability("NAMESPACE"), "One capability missed.");
            final List<String> authValues = capa.getCapability("AUTH");
            Assert.assertNotNull(authValues, "One Auth value missed.");
            Assert.assertEquals(authValues.size(), 3, "One Auth value missed");
            Assert.assertEquals(authValues.get(0), "PLAIN", "One Auth value missed");
            Assert.assertEquals(authValues.get(1), "XOAUTH2", "One Auth value missed");
            Assert.assertEquals(authValues.get(2), "OAUTHBEARER", "One Auth value missed");

            // first is junk response
            final IMAPResponse resp1 = it.next();
            Assert.assertNotNull(resp1, "Result mismatched.");
            Assert.assertEquals(resp1, serverRespJunk, "response mismatched.");

            // 2nd is a tagged response but tag does not match a2
            final IMAPResponse resp2 = it.next();
            Assert.assertNotNull(resp2, "Result mismatched.");
            Assert.assertEquals(resp2, anotherTaggedResp, "response mismatched.");

            final IMAPResponse endingResp = it.next();
            Assert.assertNotNull(endingResp, "Result mismatched.");
            Assert.assertTrue(endingResp.isOK(), "Response.isOK() mismatched.");
            Assert.assertEquals(endingResp.getTag(), "a2", "tag mismatched.");
            // verify logging messages
            final ArgumentCaptor<Object> allArgsCapture = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger, Mockito.times(5)).debug(Mockito.anyString(), allArgsCapture.capture(), allArgsCapture.capture(),
                    allArgsCapture.capture());

            // since it is vargs, 5 calls with 3 parameters all accumulate to one list
            final List<Object> logArgs = allArgsCapture.getAllValues();
            Assert.assertNotNull(logArgs, "log messages mismatched.");
            Assert.assertEquals(logArgs.size(), 15, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(0), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(1), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(2), "a2 CAPABILITY\r\n", "log messages from client mismatched.");
            Assert.assertEquals(logArgs.get(3), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(4), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(5), "* CAPABILITY IMAP4rev1 SASL-IR AUTH=PLAIN AUTH=XOAUTH2 AUTH=OAUTHBEARER ID MOVE NAMESPACE",
                    "log messages from server mismatched.");
            Assert.assertEquals(logArgs.get(6), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(7), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(8), "@@@@@* some junk MOVE NAMESPACE", "Error message mismatched.");
            Assert.assertEquals(logArgs.get(9), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(10), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(11), "a1 OK but not the tag u sent!", "Error message mismatched.");
            Assert.assertEquals(logArgs.get(12), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(13), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(14), "a2 OK CAPABILITY completed", "Error message mismatched.");

            Assert.assertNotNull(response.get());
        }

        // perform close session
        Mockito.when(closePromise.isSuccess()).thenReturn(true);
        final ImapFuture<Boolean> closeFuture = aSession.close();
        final ArgumentCaptor<ImapChannelClosedListener> listenerCaptor = ArgumentCaptor.forClass(ImapChannelClosedListener.class);
        Mockito.verify(closePromise, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of ImapChannelClosedListener.");
        final ImapChannelClosedListener closeListener = listenerCaptor.getAllValues().get(0);
        closeListener.operationComplete(closePromise);
        // close future should be done successfully
        Assert.assertTrue(closeFuture.isDone(), "close future should be done");
        final Boolean closeResp = closeFuture.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(closeResp, "close() response mismatched.");
        Assert.assertTrue(closeResp, "close() response mismatched.");
    }

    /**
     * Tests that the error callback is called.
     *
     * @throws IOException will not throw
     * @throws ImapAsyncClientException will not throw
     * @throws ProtocolException will not throw
     */
    @Test
    public void testExecuteErrorCallback()
            throws ImapAsyncClientException, IOException, ProtocolException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise authWritePromise = Mockito.mock(ChannelPromise.class); // first
        final ChannelPromise authWritePromise2 = Mockito.mock(ChannelPromise.class); // after +
        final ChannelPromise capaWritePromise = Mockito.mock(ChannelPromise.class);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(authWritePromise).thenReturn(authWritePromise2).thenReturn(capaWritePromise)
                .thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(false);

        // construct, both class level and session level debugging are off

        final String sessionCtx = USER_ID;
        final ImapAsyncSessionImpl aSession = new ImapAsyncSessionImpl(clock, channel, logger, DebugMode.DEBUG_OFF, SESSION_ID, pipeline, sessionCtx);

        // execute Authenticate plain command
        {
            final Map<String, List<String>> capas = new HashMap<String, List<String>>();
            final ImapRequest cmd = new AuthPlainCommand("orange", "juicy", new Capability(capas));
            final AtomicReference<Exception> error = new AtomicReference<>();
            final ImapFuture<ImapAsyncResponse> future = aSession.execute(cmd, new Consumer<ImapAsyncResponse>() {
                @Override
                public void accept(final ImapAsyncResponse imapAsyncResponse) {

                }
            }, new Consumer<Exception>() {
                @Override
                public void accept(final Exception e) {
                    error.set(e);
                }
            }, new Runnable() {
                @Override
                public void run() {

                }
            });
            Mockito.verify(authWritePromise, Mockito.times(1)).addListener(Mockito.any(ImapAsyncSessionImpl.class));
            Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
            Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

            // simulate write to server completed successfully
            Mockito.when(authWritePromise.isSuccess()).thenReturn(false);
            aSession.operationComplete(authWritePromise);

            // handle server response
            final IMAPResponse serverResp1 = new IMAPResponse("+");
            // following will call getNextCommandLineAfterContinuation
            aSession.handleChannelResponse(serverResp1);

            Assert.assertNotNull(error.get());
        }
    }

    /**
     * Tests that the cancel callback is called.
     *
     * @throws ImapAsyncClientException will not throw
     */
    @Test
    public void testExecuteCancellationCallback() throws ImapAsyncClientException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise authWritePromise = Mockito.mock(ChannelPromise.class); // first
        final ChannelPromise authWritePromise2 = Mockito.mock(ChannelPromise.class); // after +
        final ChannelPromise capaWritePromise = Mockito.mock(ChannelPromise.class);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(authWritePromise).thenReturn(authWritePromise2).thenReturn(capaWritePromise)
                .thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(false);

        // construct, both class level and session level debugging are off

        final String sessionCtx = USER_ID;
        final ImapAsyncSessionImpl aSession = new ImapAsyncSessionImpl(clock, channel, logger, DebugMode.DEBUG_OFF, SESSION_ID, pipeline, sessionCtx);

        // execute Authenticate plain command
        {
            final Map<String, List<String>> capas = new HashMap<String, List<String>>();
            final ImapRequest cmd = new AuthPlainCommand("orange", "juicy", new Capability(capas));
            final AtomicBoolean called = new AtomicBoolean(false);
            final ImapFuture<ImapAsyncResponse> future = aSession.execute(cmd, new Consumer<ImapAsyncResponse>() {
                @Override
                public void accept(final ImapAsyncResponse imapAsyncResponse) {

                }
            }, new Consumer<Exception>() {
                @Override
                public void accept(final Exception e) {

                }
            }, new Runnable() {
                @Override
                public void run() {
                    called.set(true);
                }
            });
            future.cancel(true);
            Assert.assertTrue(called.get());
        }
    }

}