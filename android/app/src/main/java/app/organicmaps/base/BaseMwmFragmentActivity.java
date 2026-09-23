package app.organicmaps.base;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.SplashActivity;
import app.organicmaps.sdk.util.log.Logger;
import app.organicmaps.util.ScreenAdjustments;
import java.util.Objects;

public abstract class BaseMwmFragmentActivity extends AppCompatActivity
{
  private static final String TAG = BaseMwmFragmentActivity.class.getSimpleName();

  private boolean mSafeCreated;

  /**
   * The scale this screen was built at, see {@link ScreenAdjustments#getScreenScale(Context)}.
   * <p>
   * A density cannot be changed on a screen that already exists, so it is remembered here to
   * notice when it is no longer the one the settings ask for - see {@link #onResume}.
   */
  private float mAppliedScale = 1f;

  @Override
  protected void attachBaseContext(@NonNull Context newBase)
  {
    // Before super so the very first window is already in the dp space the settings describe.
    mAppliedScale = ScreenAdjustments.getScreenScale(newBase);
    super.attachBaseContext(ScreenAdjustments.wrapContext(newBase));
  }

  @Override
  protected void onResume()
  {
    super.onResume();

    // The screen margins can be changed from the settings screen while this one sits behind it,
    // so they are re-read on the way back. Applying them is idempotent: a screen that has not
    // moved is simply padded with the same values again.
    applyScreenMargins();

    // Every screen is built in the dp space the settings described when it was created, so one
    // that is already up has to be built again when the *Adapt to screen* switch is flipped
    // somewhere else. That is the ordinary case, not an edge one: the map is sitting behind the
    // settings screen while the switch is turned on.
    if (!isFinishing() && mAppliedScale != ScreenAdjustments.getScreenScale(this))
      recreate();
  }

  /**
   * A screen that handles its own configuration changes - rotation, a new display density - keeps
   * the dp space it was built in, so the scale is checked once more here: one that no longer
   * matches the settings is rebuilt instead of being left drawn at the old size.
   */
  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig)
  {
    super.onConfigurationChanged(newConfig);
    applyScreenMargins();
    if (!isFinishing() && mAppliedScale != ScreenAdjustments.getScreenScale(this))
      recreate();
  }

  /**
   * Moves this activity's content away from the screen edges by the configured margins.
   * <p>
   * Called on every resume, and again by the settings screen while a margin is being dialled -
   * which is why the result of a change is visible there while it is being made: the whole screen
   * (header included) moves, exactly as the overlays of the map will.
   * <p>
   * The map activity overrides this to hold only what it draws <i>on top of</i> the map clear of
   * the edges; the map itself keeps the whole screen.
   */
  public void applyScreenMargins()
  {
    ScreenAdjustments.applyToContent(this);
  }

  /**
   * Shows splash screen and initializes the core in case when it was not initialized.
   * <p>
   * Do not override this method!
   * Use {@link #onSafeCreate(Bundle savedInstanceState)}
   */
  @CallSuper
  @Override
  protected final void onCreate(@Nullable Bundle savedInstanceState)
  {
    EdgeToEdge.enable(this, getStatusBarStyle());
    super.onCreate(savedInstanceState);
    if (!MwmApplication.from(this).getOrganicMaps().arePlatformAndCoreInitialized())
    {
      final Intent intent = Objects.requireNonNull(getIntent());
      prepareIntentForCoreRestart(intent, savedInstanceState);
      intent.setComponent(new ComponentName(this, SplashActivity.class));
      startActivity(intent);
      finish();
      return;
    }

    onSafeCreate(savedInstanceState);
  }

  /**
   * Preserves activity-specific state when a restored activity must restart the core through
   * {@link SplashActivity}. The new activity created after initialization does not receive this
   * instance's saved state.
   * <p>
   * Staying silent means "the state of this intent is unknown", which lets {@link SplashActivity}
   * guess. Override whenever this screen's intent can carry a payload that MwmActivity acts on.
   */
  protected void prepareIntentForCoreRestart(@NonNull Intent intent, @Nullable Bundle savedInstanceState) {}

  /**
   * Status-bar style passed to {@link EdgeToEdge#enable}. The default uses light (white)
   * icons, which contrast with the dark {@code ?colorPrimary} toolbar that most activities show
   * behind the transparent status bar. Override in activities whose status bar overlays a
   * different background (e.g. the map surface).
   */
  @NonNull
  protected SystemBarStyle getStatusBarStyle()
  {
    return SystemBarStyle.dark(Color.TRANSPARENT);
  }

  /**
   * Use this safe method instead of {@link #onCreate(Bundle savedInstanceState)}.
   * When this method is called, the core is already initialized.
   */
  @CallSuper
  protected void onSafeCreate(@Nullable Bundle savedInstanceState)
  {
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    final int layoutId = getContentLayoutResId();
    if (layoutId != 0)
      setContentView(layoutId);

    attachDefaultFragment();
    mSafeCreated = true;
  }

  @CallSuper
  @Override
  protected final void onDestroy()
  {
    super.onDestroy();

    if (!mSafeCreated)
      return;

    onSafeDestroy();
  }

  /**
   * Use this safe method instead of {@link #onDestroy()}.
   * When this method is called, the core is already initialized and
   * {@link #onSafeCreate(Bundle savedInstanceState)} was called.
   */
  @CallSuper
  protected void onSafeDestroy()
  {
    mSafeCreated = false;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    if (item.getItemId() == android.R.id.home)
    {
      onHomeOptionItemSelected();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  protected void onHomeOptionItemSelected()
  {
    onBackPressed();
  }

  protected Toolbar getToolbar()
  {
    return findViewById(R.id.toolbar);
  }

  protected void displayToolbarAsActionBar()
  {
    setSupportActionBar(getToolbar());
  }

  @Override
  public void onBackPressed()
  {
    if (getFragmentClass() == null)
    {
      super.onBackPressed();
      return;
    }
    FragmentManager manager = getSupportFragmentManager();
    String name = getFragmentClass().getName();
    Fragment fragment = manager.findFragmentByTag(name);

    if (fragment == null)
    {
      super.onBackPressed();
      return;
    }

    if (onBackPressedInternal(fragment))
      return;

    super.onBackPressed();
  }

  private boolean onBackPressedInternal(@NonNull Fragment currentFragment)
  {
    try
    {
      OnBackPressListener listener = (OnBackPressListener) currentFragment;
      return listener.onBackPressed();
    }
    catch (ClassCastException e)
    {
      Logger.i(TAG, "Fragment '" + currentFragment + "' doesn't handle back press by itself.");
      return false;
    }
  }

  /**
   * Override to set custom content view.
   * @return layout resId.
   */
  protected int getContentLayoutResId()
  {
    return 0;
  }

  protected void attachDefaultFragment()
  {
    Class<? extends Fragment> clazz = getFragmentClass();
    if (clazz != null)
      replaceFragment(clazz, getIntent().getExtras(), null);
  }

  /**
   * Replace attached fragment with the new one.
   */
  public void replaceFragment(@NonNull Class<? extends Fragment> fragmentClass, @Nullable Bundle args,
                              @Nullable Runnable completionListener)
  {
    final int resId = getFragmentContentResId();
    if (resId <= 0 || findViewById(resId) == null)
      throw new IllegalStateException(
          "Fragment can't be added, since getFragmentContentResId() isn't implemented or returns wrong resourceId.");

    String name = fragmentClass.getName();
    Fragment potentialInstance = getSupportFragmentManager().findFragmentByTag(name);
    if (potentialInstance == null)
    {
      final FragmentManager manager = getSupportFragmentManager();
      final FragmentFactory factory = manager.getFragmentFactory();
      final Fragment fragment = factory.instantiate(getClassLoader(), name);
      fragment.setArguments(args);
      manager.beginTransaction().replace(resId, fragment, name).commitAllowingStateLoss();
      manager.executePendingTransactions();
      if (completionListener != null)
        completionListener.run();
    }
  }

  /**
   * Override to automatically attach fragment in onCreate. Tag applied to fragment in back stack is set to fragment
   * name, too. WARNING : if custom layout for activity is set, getFragmentContentResId() must be implemented, too.
   * @return class of the fragment, eg FragmentClass.getClass()
   */
  protected Class<? extends Fragment> getFragmentClass()
  {
    return null;
  }

  /**
   * Get resource id for the fragment. That must be implemented to return correct resource id, if custom layout is set.
   * @return resourceId for the fragment
   */
  protected int getFragmentContentResId()
  {
    return android.R.id.content;
  }
}
