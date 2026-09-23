package app.organicmaps.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.TwoStatePreference;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.downloader.OnmapDownloader;
import app.organicmaps.editor.LanguagesFragment;
import app.organicmaps.editor.ProfileActivity;
import app.organicmaps.help.HelpActivity;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.editor.OsmOAuth;
import app.organicmaps.sdk.editor.data.Language;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.routing.RoutingOptions;
import app.organicmaps.sdk.search.SearchRecents;
import app.organicmaps.sdk.settings.MapLanguageCode;
import app.organicmaps.sdk.settings.UnitLocale;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.NetworkPolicy;
import app.organicmaps.sdk.util.PowerManagment;
import app.organicmaps.sdk.util.SharedPropertiesUtils;
import app.organicmaps.sdk.util.log.LogsManager;
import app.organicmaps.util.ScreenAdjustments;
import app.organicmaps.util.ScreenAdjustments.Edge;
import app.organicmaps.util.ThemeSwitcher;
import app.organicmaps.util.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsPrefsFragment extends BaseXmlSettingsFragment implements LanguagesFragment.Listener
{
  @Override
  protected int getXmlResources()
  {
    return R.xml.prefs_main;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);

    initStoragePrefCallbacks();
    initMeasureUnitsPrefsCallbacks();
    initZoomPrefsCallbacks();
    initMapStylePrefsCallbacks();
    initAutoDownloadPrefsCallbacks();
    initLargeFontSizePrefsCallbacks();
    initTransliterationPrefsCallbacks();
    init3dModePrefsCallbacks();
    initPerspectivePrefsCallbacks();
    initAutoZoomPrefsCallbacks();
    initLoggingEnabledPrefsCallbacks();
    initEmulationBadStorage();
    initUseMobileDataPrefsCallbacks();
    initPowerManagementPrefsCallbacks();
    initBookmarksTextPlacementPrefsCallbacks();
    initShowDownloadedRegionsPrefsCallbacks();
    initPlayServicesPrefsCallbacks();
    initSearchPrivacyPrefsCallbacks();
    initScreenSleepEnabledPrefsCallbacks();
    initShowOnLockScreenPrefsCallbacks();
    initNightNavigationPrefsCallbacks();
    initAdaptToScreenPrefsCallbacks();
    initScreenMarginsPrefsCallbacks();
  }

  private void updateVoiceInstructionsPrefsSummary()
  {
    final Preference pref = getPreference(getString(R.string.pref_tts_screen));
    pref.setSummary(Config.TTS.isEnabled() ? R.string.on : R.string.off);
  }

  private void updateMapLanguageCodeSummary()
  {
    final Preference pref = getPreference(getString(R.string.pref_map_locale));
    Locale locale = new Locale(MapLanguageCode.getMapLanguageCode());
    pref.setSummary(locale.getDisplayLanguage());
  }

  private void updateRoutingSettingsPrefsSummary()
  {
    final Preference pref = getPreference(getString(R.string.prefs_routing));
    pref.setSummary(RoutingOptions.hasAnyOptions() ? R.string.on : R.string.off);
  }

  private void updateProfileSettingsPrefsSummary()
  {
    final Preference pref = getPreference(getString(R.string.pref_osm_profile));
    if (OsmOAuth.isAuthorized())
    {
      final String username = OsmOAuth.getUsername();
      pref.setSummary(username);
    }
    else
      pref.setSummary(R.string.not_signed_in);
  }

  @Override
  public void onResume()
  {
    super.onResume();

    updateProfileSettingsPrefsSummary();
    updateVoiceInstructionsPrefsSummary();
    updateRoutingSettingsPrefsSummary();
    updateMapLanguageCodeSummary();
    updateScreenMarginsSummary();
  }

  @Override
  public boolean onPreferenceTreeClick(Preference preference)
  {
    final String key = preference.getKey();
    if (key != null)
    {
      if (key.equals(getString(R.string.pref_osm_profile)))
      {
        startActivity(new Intent(requireActivity(), ProfileActivity.class));
      }
      else if (key.equals(getString(R.string.pref_tts_screen)))
      {
        getSettingsActivity().stackFragment(VoiceInstructionsSettingsFragment.class,
                                            getString(R.string.pref_tts_enable_title), null);
      }
      else if (key.equals(getString(R.string.pref_bg_tiles_screen)))
      {
        getSettingsActivity().stackFragment(BgTilesSettingsFragment.class, getString(R.string.pref_bg_tiles_title),
                                            null);
      }
      else if (key.equals(getString(R.string.pref_help)))
      {
        startActivity(new Intent(requireActivity(), HelpActivity.class));
      }
      else if (key.equals(getString(R.string.pref_map_locale)))
      {
        LanguagesFragment langFragment = (LanguagesFragment) getSettingsActivity().stackFragment(
            LanguagesFragment.class, getString(R.string.change_map_locale), null);
        langFragment.setListener(this);
      }
    }
    return super.onPreferenceTreeClick(preference);
  }

  private void initLargeFontSizePrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_large_fonts_size));

    ((TwoStatePreference) pref).setChecked(Config.isLargeFontsSize());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      final boolean oldVal = Config.isLargeFontsSize();
      final boolean newVal = (Boolean) newValue;
      if (oldVal != newVal)
        Config.setLargeFontsSize(newVal);

      return true;
    });
  }

  private void initTransliterationPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_transliteration));

    ((TwoStatePreference) pref).setChecked(Config.isTransliteration());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      final boolean oldVal = Config.isTransliteration();
      final boolean newVal = (Boolean) newValue;
      if (oldVal != newVal)
        Config.setTransliteration(newVal);

      return true;
    });
  }

  private void initUseMobileDataPrefsCallbacks()
  {
    final ListPreference mobilePref = getPreference(getString(R.string.pref_use_mobile_data));

    NetworkPolicy.Type curValue = Config.getUseMobileDataSettings();
    if (curValue == NetworkPolicy.Type.NOT_TODAY || curValue == NetworkPolicy.Type.TODAY)
      curValue = NetworkPolicy.Type.ASK;
    mobilePref.setValue(curValue.name());
    mobilePref.setOnPreferenceChangeListener((preference, newValue) -> {
      final String valueStr = (String) newValue;
      NetworkPolicy.Type type = NetworkPolicy.Type.valueOf(valueStr);
      Config.setUseMobileDataSettings(type);
      return true;
    });
  }

  private void initPowerManagementPrefsCallbacks()
  {
    final ListPreference powerManagementPref = getPreference(getString(R.string.pref_power_management));

    @PowerManagment.SchemeType
    int curValue = PowerManagment.getScheme();
    powerManagementPref.setValue(String.valueOf(curValue));

    powerManagementPref.setOnPreferenceChangeListener((preference, newValue) -> {
      @PowerManagment.SchemeType
      int scheme = Integer.parseInt((String) newValue);

      PowerManagment.setScheme(scheme);

      disableOrEnable3DBuildingsForPowerMode(scheme);

      return true;
    });
  }

  private void initLoggingEnabledPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_enable_logging));

    ((TwoStatePreference) pref).setChecked(LogsManager.INSTANCE.isFileLoggingEnabled());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      if (!LogsManager.INSTANCE.setFileLoggingEnabled((Boolean) newValue))
      {
        // It's a very rare condition when debugging, so we can do without translation.
        Utils.showSnackbar(requireView(), "ERROR: Can't create a logs folder!");
        return false;
      }
      return true;
    });
  }

  private void initEmulationBadStorage()
  {
    final Preference pref = findPreference(getString(R.string.pref_emulate_bad_external_storage));
    if (pref == null)
      return;

    if (!SharedPropertiesUtils.shouldShowEmulateBadStorageSetting())
      removePreference(getString(R.string.pref_settings_general), pref);
    else
      pref.setVisible(true);
  }

  private void initAutoZoomPrefsCallbacks()
  {
    final TwoStatePreference pref = getPreference(getString(R.string.pref_auto_zoom));

    boolean autozoomEnabled = Framework.nativeGetAutoZoomEnabled();
    pref.setChecked(autozoomEnabled);
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      Framework.nativeSetAutoZoomEnabled((boolean) newValue);
      return true;
    });
  }

  private void initPlayServicesPrefsCallbacks()
  {
    final Preference pref = findPreference(getString(R.string.pref_play_services));
    if (pref == null)
      return;

    if (!MwmApplication.from(requireContext()).getLocationHelper().isGmsLocationProviderAvailable())
      removePreference(getString(R.string.pref_privacy), pref);
    else
    {
      ((TwoStatePreference) pref).setChecked(Config.useGoogleServices());
      pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @SuppressLint("MissingPermission")
        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, Object newValue)
        {
          final LocationHelper locationHelper = MwmApplication.from(requireContext()).getLocationHelper();
          boolean oldVal = Config.useGoogleServices();
          boolean newVal = (Boolean) newValue;
          if (oldVal != newVal)
          {
            Config.setUseGoogleService(newVal);
            if (locationHelper.isActive())
            {
              locationHelper.stop();
              locationHelper.start();
            }
          }
          return true;
        }
      });
    }
  }

  private void initSearchPrivacyPrefsCallbacks()
  {
    final Preference pref = findPreference(getString(R.string.pref_search_history));
    if (pref == null)
      return;

    final boolean isHistoryEnabled = Config.isSearchHistoryEnabled();
    ((TwoStatePreference) pref).setChecked(isHistoryEnabled);
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      boolean newVal = (Boolean) newValue;
      if (newVal != Config.isSearchHistoryEnabled())
      {
        Config.setSearchHistoryEnabled(newVal);
        if (newVal)
          SearchRecents.refresh();
        else
          SearchRecents.clear();
      }
      return true;
    });
  }

  private void init3dModePrefsCallbacks()
  {
    final TwoStatePreference pref = getPreference(getString(R.string.pref_3d_buildings));

    final Framework.Params3dMode _3d = new Framework.Params3dMode();
    Framework.nativeGet3dMode(_3d);

    // Read power managements preference.
    final ListPreference powerManagementPref = getPreference(getString(R.string.pref_power_management));
    final String powerManagementValueStr = powerManagementPref.getValue();
    final Integer powerManagementValue =
        (powerManagementValueStr != null) ? Integer.parseInt(powerManagementValueStr) : null;
    disableOrEnable3DBuildingsForPowerMode(powerManagementValue);

    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      Framework.Params3dMode current = new Framework.Params3dMode();
      Framework.nativeGet3dMode(current);
      Framework.nativeSet3dMode(current.enabled, (Boolean) newValue);
      return true;
    });
  }

  // Argument `powerManagementValue` could be null on the first app run.
  private void disableOrEnable3DBuildingsForPowerMode(@Nullable Integer powerManagementValue)
  {
    final TwoStatePreference pref = getPreference(getString(R.string.pref_3d_buildings));

    if (powerManagementValue != null && powerManagementValue == PowerManagment.HIGH)
    {
      pref.setShouldDisableView(true);
      pref.setEnabled(false);
      pref.setSummary(getString(R.string.pref_map_3d_buildings_disabled_summary));
      pref.setChecked(false);
    }
    else
    {
      final Framework.Params3dMode _3d = new Framework.Params3dMode();
      Framework.nativeGet3dMode(_3d);

      pref.setShouldDisableView(false);
      pref.setEnabled(true);
      pref.setSummary("");
      pref.setChecked(_3d.buildings);
    }
  }

  private void initPerspectivePrefsCallbacks()
  {
    final TwoStatePreference pref = getPreference(getString(R.string.pref_3d));

    final Framework.Params3dMode _3d = new Framework.Params3dMode();
    Framework.nativeGet3dMode(_3d);

    pref.setChecked(_3d.enabled);

    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      Framework.Params3dMode current = new Framework.Params3dMode();
      Framework.nativeGet3dMode(current);
      Framework.nativeSet3dMode((Boolean) newValue, current.buildings);
      return true;
    });
  }

  private void initAutoDownloadPrefsCallbacks()
  {
    final TwoStatePreference pref = getPreference(getString(R.string.pref_autodownload));

    pref.setChecked(Config.isAutodownloadEnabled());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      final boolean value = (boolean) newValue;
      Config.setAutodownloadEnabled(value);

      if (value)
        OnmapDownloader.setAutodownloadLocked(false);

      return true;
    });
  }

  private void initMapStylePrefsCallbacks()
  {
    final ListPreference pref = getPreference(getString(R.string.pref_map_appearance));
    final List<Config.UiTheme> availableThemes = getAvailableUiThemes();
    pref.setEntries(getThemeEntries(availableThemes));
    pref.setEntryValues(getThemeEntryValues(availableThemes));

    final Config.UiTheme currentTheme = Config.UiTheme.getUiThemePreference();
    pref.setValue(currentTheme.value);
    pref.setSummary(getString(getThemeTitle(currentTheme)));
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      final var newTheme = Config.UiTheme.ofValue((String) newValue);
      if (!Config.UiTheme.setUiThemePreference(newTheme))
        return true;

      ThemeSwitcher.INSTANCE.synchronizeApplicationTheme();
      pref.setSummary(getString(getThemeTitle(newTheme)));
      return true;
    });
  }

  @NonNull
  private List<Config.UiTheme> getAvailableUiThemes()
  {
    final List<Config.UiTheme> themes = new ArrayList<>(4);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      themes.add(Config.UiTheme.SYSTEM);
    themes.add(Config.UiTheme.LIGHT);
    themes.add(Config.UiTheme.DARK);
    themes.add(Config.UiTheme.SCHEDULED);
    return themes;
  }

  @NonNull
  private CharSequence[] getThemeEntries(@NonNull List<Config.UiTheme> themes)
  {
    final CharSequence[] entries = new CharSequence[themes.size()];
    for (int i = 0; i < themes.size(); i++)
      entries[i] = getString(getThemeTitle(themes.get(i)));
    return entries;
  }

  @NonNull
  private CharSequence[] getThemeEntryValues(@NonNull List<Config.UiTheme> themes)
  {
    final CharSequence[] entryValues = new CharSequence[themes.size()];
    for (int i = 0; i < themes.size(); i++)
      entryValues[i] = themes.get(i).value;
    return entryValues;
  }

  private int getThemeTitle(@NonNull Config.UiTheme theme)
  {
    return switch (theme)
    {
      case SYSTEM -> R.string.follow_system;
      case LIGHT -> R.string.pref_appearance_light;
      case DARK -> R.string.pref_appearance_dark;
      case SCHEDULED -> R.string.pref_appearance_scheduled;
    };
  }

  private void initZoomPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_show_zoom_buttons));

    ((TwoStatePreference) pref).setChecked(Config.showZoomButtons());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      Config.setShowZoomButtons((boolean) newValue);
      return true;
    });
  }

  private void initMeasureUnitsPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_munits));

    ((ListPreference) pref).setValue(String.valueOf(UnitLocale.getUnits()));
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      UnitLocale.setUnits(Integer.parseInt((String) newValue));
      return true;
    });
  }

  private void initStoragePrefCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_storage));
    pref.setOnPreferenceClickListener(preference -> {
      if (MapManager.nativeIsDownloading())
      {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.MwmTheme_AlertDialog)
            .setTitle(R.string.downloading_is_active)
            .setMessage(R.string.cant_change_this_setting)
            .setPositiveButton(R.string.ok, null)
            .show();
      }
      else
      {
        getSettingsActivity().stackFragment(StoragePathFragment.class, getString(R.string.maps_storage), null);
      }

      return true;
    });
  }

  private void initScreenSleepEnabledPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_keep_screen_on));

    final boolean isKeepScreenOnEnabled = Config.isKeepScreenOnEnabled();
    ((TwoStatePreference) pref).setChecked(isKeepScreenOnEnabled);
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      boolean newVal = (Boolean) newValue;
      if (isKeepScreenOnEnabled != newVal)
      {
        Config.setKeepScreenOnEnabled(newVal);
        // No need to call Utils.keepScreenOn() here, as relevant activities do it when starting / stopping.
      }
      return true;
    });
  }

  private void initShowOnLockScreenPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_show_on_lock_screen));

    final boolean isShowOnLockScreenEnabled = Config.isShowOnLockScreenEnabled();
    ((TwoStatePreference) pref).setChecked(isShowOnLockScreenEnabled);
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      boolean newVal = (Boolean) newValue;
      if (isShowOnLockScreenEnabled != newVal)
      {
        Config.setShowOnLockScreenEnabled(newVal);
        Utils.showOnLockScreen(newVal, requireActivity());
      }
      return true;
    });
  }

  private void initNightNavigationPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_auto_night_in_navigation));

    final var isAutoDarkNavigationEnabled = Config.UiTheme.isAutoDarkNavigationEnabled();
    ((TwoStatePreference) pref).setChecked(isAutoDarkNavigationEnabled);
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      var isChanged = Config.UiTheme.setAutoDarkNavigationEnabled((Boolean) newValue);
      if (isChanged)
      {
        ThemeSwitcher.INSTANCE.synchronizeApplicationTheme();
      }
      return isChanged;
    });
  }

  private void initAdaptToScreenPrefsCallbacks()
  {
    final TwoStatePreference pref = getPreference(getString(R.string.pref_adapt_to_screen));

    pref.setChecked(ScreenAdjustments.isAdaptToScreen(requireContext()));
    pref.setSummary(adaptToScreenSummary());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      ScreenAdjustments.setAdaptToScreen(requireContext(), (Boolean) newValue);
      // A density is applied before the first view is inflated, so this screen has to be built
      // again for its own result to show - which is also the point: the answer to the switch is
      // the screen it is on, not a sentence about it. Posted so the value above is written first;
      // the screens behind this one pick the new scale up when they are resumed.
      requireView().post(() -> {
        if (isAdded())
          getSettingsActivity().recreate();
      });
      return true;
    });
  }

  /**
   * The description with what the switch is drawing at in one number: "... x1.5", and "x1" while
   * it is off, so an empty cell is never what the row shows. This is the note the app-hub settings
   * row carries beside its switch - here it ends the summary instead, because a preference row has
   * no value column of its own.
   */
  @NonNull
  private CharSequence adaptToScreenSummary()
  {
    final float scale = ScreenAdjustments.getScreenScale(requireContext());
    final String number = scale == (int) scale
                          ? String.valueOf((int) scale)
                          : String.format(Locale.US, "%.1f", scale);
    return getString(R.string.adapt_to_screen_summary) + " · "
           + getString(R.string.adapt_to_screen_scale, number);
  }

  private void initScreenMarginsPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_screen_margins));
    pref.setOnPreferenceClickListener(preference -> {
      showMarginEdgePicker();
      return true;
    });
    updateScreenMarginsSummary();
  }

  /** "0 / 0 / 0 / 0 dp" - left / right / top / bottom, the order of the edges. */
  private void updateScreenMarginsSummary()
  {
    final Context context = requireContext();
    getPreference(getString(R.string.pref_screen_margins))
        .setSummary(getString(R.string.screen_margins_value,
                              ScreenAdjustments.getMarginDp(context, Edge.LEFT),
                              ScreenAdjustments.getMarginDp(context, Edge.RIGHT),
                              ScreenAdjustments.getMarginDp(context, Edge.TOP),
                              ScreenAdjustments.getMarginDp(context, Edge.BOTTOM)));
  }

  /** One row per edge, each showing the value it currently has. */
  private void showMarginEdgePicker()
  {
    final Edge[] edges = Edge.values();
    final Context context = requireContext();
    final String[] items = new String[edges.length];
    for (int i = 0; i < edges.length; i++)
      items[i] =
          getString(R.string.screen_margin_edge_value, getString(edges[i].title),
                    ScreenAdjustments.getMarginDp(context, edges[i]));

    new MaterialAlertDialogBuilder(requireActivity(), R.style.MwmTheme_AlertDialog)
        .setTitle(R.string.screen_margins_title)
        .setMessage(R.string.screen_margins_message)
        .setItems(items, (dialog, which) -> showMarginDialer(edges[which]))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  /**
   * The dial for one edge: every whole dp from 0 up to half of that side of the screen. The value
   * is applied as the bar moves and this screen is padded by the same margins as everything else,
   * so turning it moves the screen immediately - the dial stays open while it does, which is what
   * makes it a preview.
   */
  private void showMarginDialer(@NonNull Edge edge)
  {
    final Context context = requireContext();
    final View content = LayoutInflater.from(context).inflate(R.layout.dialog_margin_dialer, null, false);
    final TextView value = content.findViewById(R.id.margin_value);
    final SeekBar seek = content.findViewById(R.id.margin_seek);
    final TextView range = content.findViewById(R.id.margin_range);

    final int max = ScreenAdjustments.getMaxMarginDp(context, edge);
    final int original = ScreenAdjustments.getMarginDp(context, edge);

    value.setText(getString(R.string.screen_margin_value, original));
    range.setText(getString(R.string.screen_margin_range, max));
    seek.setMax(max);
    seek.setProgress(original);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar bar, int progress, boolean fromUser)
      {
        value.setText(getString(R.string.screen_margin_value, progress));
        if (fromUser)
          applyScreenMargin(edge, progress);
      }

      @Override
      public void onStartTrackingTouch(SeekBar bar) {}

      @Override
      public void onStopTrackingTouch(SeekBar bar) {}
    });

    new MaterialAlertDialogBuilder(requireActivity(), R.style.MwmTheme_AlertDialog)
        .setTitle(edge.title)
        .setView(content)
        .setPositiveButton(android.R.string.ok, null)
        // Cancelling puts the edge back where it was: the bar has been applied as it moved, so
        // leaving alone is not a way to undo.
        .setNegativeButton(android.R.string.cancel,
                           (dialog, which) -> applyScreenMargin(edge, original))
        .show();
  }

  /** Writes the value and shows the result at once: this screen moves with it, like every other. */
  private void applyScreenMargin(@NonNull Edge edge, int dp)
  {
    ScreenAdjustments.setMarginDp(requireContext(), edge, dp);
    getSettingsActivity().applyScreenMargins();
    updateScreenMarginsSummary();
  }

  private void initBookmarksTextPlacementPrefsCallbacks()
  {
    final ListPreference bookmarksTextPlacementPref = getPreference(getString(R.string.pref_bookmarks_text_placement));

    final int currentPlacement = Framework.nativeGetBookmarksTextPlacement();
    bookmarksTextPlacementPref.setValue(String.valueOf(currentPlacement));

    bookmarksTextPlacementPref.setOnPreferenceChangeListener((preference, newValue) -> {
      final int placement = Integer.parseInt((String) newValue);
      Framework.nativeSetBookmarksTextPlacement(placement);
      return true;
    });
  }

  private void initShowDownloadedRegionsPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_show_downloaded_regions));

    ((TwoStatePreference) pref).setChecked(Framework.nativeIsShowDownloadedRegions());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      Framework.nativeSetShowDownloadedRegions((boolean) newValue);
      return true;
    });
  }

  private void removePreference(@NonNull String categoryKey, @NonNull Preference preference)
  {
    final PreferenceCategory category = getPreference(categoryKey);

    category.removePreference(preference);
  }

  @Override
  public void onLanguageSelected(Language language)
  {
    MapLanguageCode.setMapLanguageCode(language.code);
    getSettingsActivity().onBackPressed();
  }
}
