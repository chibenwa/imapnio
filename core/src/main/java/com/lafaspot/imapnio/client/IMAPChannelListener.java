package com.lafaspot.imapnio.client;

import com.lafaspot.imapnio.listener.IMAPConnectionListener;
import com.lafaspot.logfast.logging.LogDataUtil;
import com.lafaspot.logfast.logging.Logger;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * Listener for the channel.
 *
 * @author kraman
 *
 */
public class IMAPChannelListener implements ChannelHandler {

    /** The IMAP session. */
    private final IMAPSession session;

    /** logger. */
    private final Logger log;

    /**
     * Constructs a listener.
     *
     * @param session
     *            IMAP session
     */
    public IMAPChannelListener(final IMAPSession session) {
        this.session = session;
        log = session.getLogger();
    }

    /**
     * Callback when a new handler has been added to channel.
     *
     * @param ctx
     *            the channel handler context
     */
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (log.isDebug()) {
            log.debug(new LogDataUtil().set(this.getClass(), "channel - added"), null);
        }
    }

    /**
     * A channel has been removed, socket disconnect event. Call the client listener if one was registered.
     *
     * @param ctx
     *            the channel handler context
     */
    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        if (log.isDebug()) {
            log.debug(new LogDataUtil().set(this.getClass(), "channel closed - removed"), null);
        }
        if (null != session && session.getConnectionListener() != null) {
            ((IMAPConnectionListener) session.getConnectionListener()).onDisconnect(session);
        }
    }

    /**
     * Got exception from channel. Call the client listener if one was registered.
     *
     * @param ctx
     *            context
     * @param cause
     *            failure reason
     */
    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (log.isDebug()) {
            log.debug(new LogDataUtil().set(this.getClass(), "channel closed - exception"), cause);
        }
        if (null != session && session.getConnectionListener() != null) {
            ((IMAPConnectionListener) session.getConnectionListener()).onDisconnect(session);
        }
    }

}
