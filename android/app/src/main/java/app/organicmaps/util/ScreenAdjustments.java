package app.organicmaps.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import app.organicmaps.R;

/**
 * The app's two display settings: <b>Adapt to screen</b> and <b>Screen margins</b>.
 * <p>
 * Both are read very early - the first one from {@code attachBaseContext}, before any view is
 * inflated and long before the C++ core is initialized - which is why they live in their own
 * SharedPreferences file rather than behind the usual config helpers.
 * <p>
 * The mechanics mirror the app-hub implementation of the same two options: the adaptation moves
 * the whole app into a denser dp space (one lever for icons, text and spacing at once), and the
 * margins push the content away from the screen edges so a launcher overlay - a shortcut rail, a
 * clock, a climate strip - cannot cover it.
 */
public final class ScreenAdjustments
{
  private static final String PREFS_FILE = "screen_adjustments";
  private static final String KEY_ADAPT = "adapt_screen";

  /** The width in dp a phone has, which is what the screen is measured against. */
  private static final float REFERENCE_WIDTH_DP = 400f;

  /** How far the adaptation may go, so a very wide panel is not turned into a phone. */
  private static final float MAX_SCALE = 2f;

  /**
   * One edge of the screen, with the key its value is stored under.
   * <p>
   * The four edges are physical, not {@code start}/{@code end}: what hides part of the screen is a
   * rail on one side of the display, and that side does not move when the layout direction is
   * flipped.
   */
  public enum Edge
  {
    LEFT(R.string.screen_margin_left, "margin_left"),
    RIGHT(R.string.screen_margin_right, "margin_right"),
    TOP(R.string.screen_margin_top, "margin_top"),
    BOTTOM(R.string.screen_margin_bottom, "margin_bottom");

    @StringRes
    public final int title;

    private final String key;

    Edge(@StringRes int title, @NonNull String key)
    {
      this.title = title;
      this.key = key;
    }
  }

  private ScreenAdjustments() {}

  /**
   * The context the settings are read from. Always the application's, so a screen whose own
   * configuration has already been rescaled still asks about the device it is running on.
   */
  @NonNull
  private static Context appContext(@NonNull Context context)
  {
    final Context app = context.getApplicationContext();
    return app != null ? app : context;
  }

  @NonNull
  private static SharedPreferences prefs(@NonNull Context context)
  {
    return appContext(context).getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
  }

  /**
   * Draw everything at a size that suits the screen this app is running on.
   * <p>
   * Not a size of its own: a switch, and it applies to the whole app - the icons, the text, and
   * every padding and margin in the layouts, because what it changes is the dp space they are all
   * written in (see {@link #getScreenScale}).
   */
  public static boolean isAdaptToScreen(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_ADAPT, false);
  }

  public static void setAdaptToScreen(@NonNull Context context, boolean adapt)
  {
    prefs(context).edit().putBoolean(KEY_ADAPT, adapt).apply();
  }

  /**
   * The screen's own width in dp, before anything of ours is applied.
   * <p>
   * Read from the <i>application's</i> configuration rather than from a screen's:
   * {@link #wrapContext} rewrites the width of every configuration this app is inflated with, and
   * the number that decides how much to scale must never be a scaled one - a second pass through
   * it would compound the two.
   */
  private static int getDeviceWidthDp(@NonNull Context context)
  {
    final Context app = appContext(context);
    final Configuration config = app.getResources().getConfiguration();
    if (config.screenWidthDp > 0)
      return config.screenWidthDp;

    // Some devices report 0 there; fall back to the real pixels.
    final DisplayMetrics metrics = app.getResources().getDisplayMetrics();
    if (metrics.density > 0f)
      return (int) (metrics.widthPixels / metrics.density);
    return metrics.widthPixels;
  }

  /**
   * How much of everything this app draws is scaled up on this screen: 1 while the switch is off.
   * <p>
   * The app is written in dp, and a dp is a fixed fraction of nothing: on a 1024dp-wide panel the
   * 64dp icon that fills a third of a phone covers a sixteenth of the screen, viewed from further
   * away than any phone is. This is that width over the width a phone has, so an icon keeps the
   * share of the <i>display</i> it would have on a phone instead of the share of whatever dp space
   * the panel claims - and it is capped, because a 4K panel should not be turned into a phone.
   */
  public static float getScreenScale(@NonNull Context context)
  {
    if (!isAdaptToScreen(context))
      return 1f;
    final int widthDp = getDeviceWidthDp(context);
    if (widthDp <= 0)
      return 1f;
    return Math.min(Math.max(widthDp / REFERENCE_WIDTH_DP, 1f), MAX_SCALE);
  }

  /**
   * The device's own density: dp to pixels for the few things that must <b>not</b> follow
   * {@link #getScreenScale}.
   * <p>
   * The screen margins are one of those. What they keep clear of this app's content is a rail the
   * launcher draws at a fixed size, and it does not grow when the icons do - so a stored dp has to
   * keep meaning the same pixels whichever way the adaptation is set.
   */
  private static float getDeviceDensity(@NonNull Context context)
  {
    return appContext(context).getResources().getDisplayMetrics().density;
  }

  /**
   * {@code base} wrapped so that the screen can be adapted to. Only the density and the screen
   * size derived from it are changed - locale, layout direction and the rest of the configuration
   * stay exactly as they are.
   */
  @NonNull
  public static Context wrapContext(@NonNull Context base)
  {
    final float scale = getScreenScale(base);
    if (scale <= 1f)
      return base;

    final Configuration config = new Configuration(base.getResources().getConfiguration());
    adaptDensity(config, base.getResources().getDisplayMetrics().densityDpi, scale);
    return base.createConfigurationContext(config);
  }

  /**
   * Moves a configuration into a denser dp space, which is what scales everything at once: an icon
   * size in dp, a text size in sp (the scaled density follows the density), and every padding,
   * margin, corner and cell in the layouts. One lever instead of one per dimension, so nothing can
   * be left behind at the old size.
   * <p>
   * The screen's own size in dp is a function of the density, so it is recomputed here as well:
   * the framework does not do it for a configuration an app overrides, and a grid still laying its
   * cells out for the old, much wider screen would draw eight columns into the room of four.
   */
  private static void adaptDensity(@NonNull Configuration config, int baseDpi, float scale)
  {
    final int dpi = config.densityDpi > 0 ? config.densityDpi : baseDpi;
    config.densityDpi = Math.round(dpi * scale);

    config.screenWidthDp = denserDp(config.screenWidthDp, scale);
    config.screenHeightDp = denserDp(config.screenHeightDp, scale);
    final int smallest = denserDp(config.smallestScreenWidthDp, scale);
    config.smallestScreenWidthDp = smallest;

    // Which of small/normal/large/xlarge a screen is, is decided by that same width, so it is
    // brought in line with it - otherwise resources would be picked for a size the app is no
    // longer laid out at.
    if (smallest > 0)
    {
      final int cleared = config.screenLayout & ~Configuration.SCREENLAYOUT_SIZE_MASK;
      config.screenLayout = cleared | sizeClass(smallest);
    }
  }

  /** The same side of the screen, measured in the new dp. */
  private static int denserDp(int dp, float scale)
  {
    return dp <= 0 ? dp : Math.round(dp / scale);
  }

  /** The AOSP thresholds, for the width the screen has after scaling. */
  private static int sizeClass(int smallestScreenWidthDp)
  {
    if (smallestScreenWidthDp >= 720)
      return Configuration.SCREENLAYOUT_SIZE_XLARGE;
    if (smallestScreenWidthDp >= 480)
      return Configuration.SCREENLAYOUT_SIZE_LARGE;
    if (smallestScreenWidthDp >= 320)
      return Configuration.SCREENLAYOUT_SIZE_NORMAL;
    return Configuration.SCREENLAYOUT_SIZE_SMALL;
  }

  /** How far one edge of this app's content is moved inwards, in dp (0 = off). */
  public static int getMarginDp(@NonNull Context context, @NonNull Edge edge)
  {
    return clamp(prefs(context).getInt(edge.key, 0), 0, getMaxMarginDp(context, edge));
  }

  public static void setMarginDp(@NonNull Context context, @NonNull Edge edge, int dp)
  {
    prefs(context).edit().putInt(edge.key, clamp(dp, 0, getMaxMarginDp(context, edge))).apply();
  }

  /**
   * The largest value one edge may take: half of that side of the screen.
   * <p>
   * A ceiling rather than a list of allowed values - any whole number below it is valid - and half
   * the side rather than the whole one because two opposite edges at the maximum would otherwise
   * have nothing left to show.
   */
  public static int getMaxMarginDp(@NonNull Context context, @NonNull Edge edge)
  {
    final boolean horizontal = edge == Edge.LEFT || edge == Edge.RIGHT;
    final Context app = appContext(context);
    final Configuration config = app.getResources().getConfiguration();
    final int screenDp = horizontal ? config.screenWidthDp : config.screenHeightDp;
    if (screenDp > 0)
      return screenDp / 2;

    // Some devices report 0 there; fall back to the real pixels.
    final DisplayMetrics metrics = app.getResources().getDisplayMetrics();
    final int pixels = horizontal ? metrics.widthPixels : metrics.heightPixels;
    final int dp = metrics.density > 0f ? (int) (pixels / metrics.density) : pixels;
    return Math.max(1, dp / 2);
  }

  private static int clamp(int value, int min, int max)
  {
    return Math.max(min, Math.min(value, max));
  }

  private static int toPx(@NonNull Context context, int dp)
  {
    return Math.round(dp * getDeviceDensity(context));
  }

  /**
   * Pads this activity's content by the configured margins - what every screen but the map does.
   * <p>
   * The margin band shows the window background behind the content, so no seam of the layout
   * itself appears when the values grow.
   */
  public static void applyToContent(@NonNull Activity activity)
  {
    final View content = activity.findViewById(android.R.id.content);
    final View root = content instanceof ViewGroup ? ((ViewGroup) content).getChildAt(0) : null;
    if (root == null)
      return;
    padByScreenMargins(root, activity);
  }

  /**
   * Moves this view's own box inwards by the configured margins. Used for the overlays around the
   * map, which are laid out against the screen edges: the map underneath keeps the whole screen,
   * only what is drawn on top of it is held clear of the edges.
   */
  public static void insetByScreenMargins(@NonNull View view, @NonNull Context context)
  {
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (!(params instanceof ViewGroup.MarginLayoutParams))
      return;

    final ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
    margins.leftMargin = toPx(context, getMarginDp(context, Edge.LEFT));
    margins.topMargin = toPx(context, getMarginDp(context, Edge.TOP));
    margins.rightMargin = toPx(context, getMarginDp(context, Edge.RIGHT));
    margins.bottomMargin = toPx(context, getMarginDp(context, Edge.BOTTOM));
    view.setLayoutParams(margins);
  }

  /**
   * Pads this view by the configured margins, so its content starts where the margins end - the
   * ordinary case: a whole screen moved away from the edges at once.
   */
  public static void padByScreenMargins(@NonNull View view, @NonNull Context context)
  {
    view.setPadding(toPx(context, getMarginDp(context, Edge.LEFT)),
                    toPx(context, getMarginDp(context, Edge.TOP)),
                    toPx(context, getMarginDp(context, Edge.RIGHT)),
                    toPx(context, getMarginDp(context, Edge.BOTTOM)));
  }
}
