package netpro.client.ui;

import javafx.application.Platform;

public final class Fx {
    private Fx() {}

    public static void run(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }
}