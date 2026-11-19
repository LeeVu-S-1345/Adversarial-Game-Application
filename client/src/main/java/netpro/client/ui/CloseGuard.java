package netpro.client.ui;

/** A screen that may ask before the window closes (the game screen during a game). */
public interface CloseGuard {
    /** True to close now. False: the screen asks itself and calls quit if the player confirms. */
    boolean confirmClose(Runnable quit);
}
