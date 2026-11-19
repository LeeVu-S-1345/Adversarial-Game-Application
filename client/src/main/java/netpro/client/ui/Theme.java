package netpro.client.ui;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

import java.io.InputStream;

/** Fonts, colors and materials shared by every screen (docs/ui-spec.md, section 1.2). */
public final class Theme {
    private Theme() {}

    public static final Color INK = Color.web("#1c1d1a");
    public static final Color DIM = Color.web("#6b6c66");
    public static final Color FAINT = Color.web("#8d8e87");
    public static final Color FELT = Color.web("#2f6b4f");
    public static final Color FELT_DEEP = Color.web("#24543d");
    public static final Color FELT_BOARD = Color.web("#2e6a4d");
    public static final Color RED = Color.web("#a3261d");
    public static final Color BLUE = Color.web("#1f4fa3");
    public static final Color PAPER = Color.web("#fdfdfa");
    public static final Color PAPER_LINE = Color.web("#c6d6ea");
    public static final Color WIN = Color.web("#f7e3a1");

    public static final String SANS = "IBM Plex Sans";
    public static final String MONO = "IBM Plex Mono Medium";

    private static final String[] AVATAR = {"#2f6b4f", "#1f4fa3", "#9a5b13", "#a3332a", "#4f5d6b", "#6b4f8a"};

    /** Loads the bundled IBM Plex fonts (SIL Open Font License, see ui/fonts/OFL.txt). */
    public static void loadFonts() {
        for (String f : new String[]{"IBMPlexSans-Regular", "IBMPlexSans-SemiBold", "IBMPlexSans-Bold",
                "IBMPlexSansCondensed-Bold", "IBMPlexMono-Medium"}) {
            try (InputStream in = Theme.class.getResourceAsStream("/netpro/client/ui/fonts/" + f + ".ttf")) {
                if (in == null || Font.loadFont(in, 14) == null) System.err.println("Font not loaded: " + f);
            } catch (Exception e) {
                System.err.println("Font not loaded: " + f + " (" + e.getMessage() + ")");
            }
        }
    }

    /** Same color for the same name on every screen. */
    public static Color avatarColor(String name) {
        int h = 0;
        for (int i = 0; i < name.length(); i++) h = (h * 31 + name.charAt(i)) % 997;
        return Color.web(AVATAR[h % AVATAR.length]);
    }

    /** Felt: a base color with a faint 4 px dot pattern. */
    public static Background felt(Color base, double radius) {
        WritableImage img = new WritableImage(4, 4);
        PixelWriter w = img.getPixelWriter();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) w.setColor(x, y, base);
        w.setColor(1, 1, base.interpolate(Color.WHITE, 0.07));
        return new Background(
                new BackgroundFill[]{new BackgroundFill(base, new CornerRadii(radius), null)},
                new BackgroundImage[]{new BackgroundImage(img, BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                        BackgroundPosition.DEFAULT, BackgroundSize.DEFAULT)});
    }

    /** Clips a region to rounded corners (background images ignore corner radii). */
    public static void roundClip(Region r, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(r.widthProperty());
        clip.heightProperty().bind(r.heightProperty());
        r.setClip(clip);
    }
}
