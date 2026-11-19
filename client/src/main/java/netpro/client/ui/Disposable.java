package netpro.client.ui;

/** A controller that runs timers or animations; SceneManager calls dispose() when its screen is replaced. */
public interface Disposable {
    void dispose();
}
