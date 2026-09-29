package wtf.woke.lite.reconnect;

/**
 * Where a reconnect attempt is in its short life cycle.
 *
 * <p>The states exist to make two promises cheap to keep: a countdown is always
 * visible and always cancellable while it runs ({@link #COUNTING}), and no
 * second connection may be started once one is in flight
 * ({@link #CONNECTING}). Anything else is either idle or explicitly given
 * up, so a caller can always tell "nothing is happening" from "something is
 * about to happen".</p>
 */
public enum ReconnectState {

    /** Nothing is pending. */
    IDLE,

    /** The countdown is running and can still be cancelled. */
    COUNTING,

    /** The connect screen has been opened; a second attempt must not start. */
    CONNECTING,

    /** The countdown was cancelled by the player or by the module going off. */
    CANCELLED
}
