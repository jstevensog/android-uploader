package com.nightscout.android.settings;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.common.base.Optional;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.nightscout.android.BuildConfig;
import com.nightscout.android.R;
import com.nightscout.android.barcode.AndroidBarcode;
import com.nightscout.android.preferences.AndroidPreferences;
import com.nightscout.android.preferences.PreferenceKeys;
import com.nightscout.android.preferences.PreferencesValidator;
import com.nightscout.core.barcode.NSBarcodeConfig;
import com.nightscout.core.preferences.NightscoutPreferences;
import com.nightscout.core.utils.RestUriUtils;

import java.lang.reflect.Field;
import java.util.List;

public class SettingsActivity extends FragmentActivity {
    private MainPreferenceFragment mainPreferenceFragment;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
        setupListener();
//        refreshFragments();
    }

    @Override
    public View onCreateView(String name, @NonNull Context context, @NonNull AttributeSet attrs) {
//        refreshFragments();
        return super.onCreateView(name, context, attrs);
    }

    private void refreshFragments() {
        mainPreferenceFragment = new MainPreferenceFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                mainPreferenceFragment).commit();
    }

    private void setupActionBar() {
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupListener() {
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (key.equals(PreferenceKeys.LABS_ENABLED) || key.equals(PreferenceKeys.DEXCOM_DEVICE_TYPE)) {
                    refreshFragments();
                }
//                Intent collector = new Intent(getApplicationContext(), CollectorService.class);
//                Intent processor = new Intent(getApplicationContext(), ProcessorService.class);
//                getApplicationContext().stopService(collector);
//                getApplicationContext().stopService(processor);
//                getApplicationContext().startService(collector);
//                getApplicationContext().startService(processor);
            }
        };
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected static void showValidationError(final Context context, final String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.invalid_input_title);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult == null || scanResult.getContents() == null) {
            return;
        }
        NightscoutPreferences prefs = new AndroidPreferences(this);
        if (scanResult.getFormatName().equals("QR_CODE")) {
            if (scanResult.getContents() != null) {
                NSBarcodeConfig barcode = new NSBarcodeConfig(scanResult.getContents());
                if (barcode.hasMongoConfig()) {
                    prefs.setMongoUploadEnabled(true);
                    if (barcode.getMongoUri().isPresent()) {
                        prefs.setMongoClientUri(barcode.getMongoUri().get());
                        prefs.setMongoCollection(barcode.getMongoCollection().orNull());
                        prefs.setMongoDeviceStatusCollection(
                                barcode.getMongoDeviceStatusCollection().orNull());
                    }
                } else {
                    prefs.setMongoUploadEnabled(false);
                }
                if (barcode.hasApiConfig()) {
                    prefs.setRestApiEnabled(true);
                    prefs.setRestApiBaseUris(barcode.getApiUris());
                } else {
                    prefs.setRestApiEnabled(false);
                }
                refreshFragments();
            }
        } else if (scanResult.getFormatName().equals("CODE_128")) {
            // TODO Assuming this is a share receiver. May get messy when medtronic devices are added
            // consider refactoring
            Log.d("XXX", "Setting serial number to: " + scanResult.getContents());
            prefs.setShareSerial(scanResult.getContents());
            refreshFragments();
        }
    }

    public static class MainPreferenceFragment extends PreferenceFragment {
        private int labTapCount = 0;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main);
            setupBarcodeConfigScanner();
            setupBarcodeShareScanner();
            setupValidation();
            setupVersionNumbers();
            setupBtScanner();
            setupDeviceOptions();
            ListPreference deviceType = (ListPreference) findPreference("dexcom_device_type");
            deviceType.setSummary(deviceType.getEntry().toString());
            setShareOptions(deviceType.getValue());
            setupLabs();
        }

        private void setupLabs() {
            AndroidPreferences prefs = new AndroidPreferences(getActivity());
            if (!prefs.areLabsEnabled()) {
                PreferenceScreen root = (PreferenceScreen) findPreference("root");
                PreferenceScreen labs = (PreferenceScreen) findPreference("labs");
                root.removePreference(labs);
            }
        }

        private void setupVersionNumbers() {
            findPreference("about_version_name").setSummary(BuildConfig.VERSION_CODENAME);
            Preference versionNumber = findPreference("about_version_number");
            versionNumber.setSummary(BuildConfig.VERSION_NAME);
            final AndroidPreferences prefs = new AndroidPreferences(getActivity());
            if (!prefs.areLabsEnabled()) {
                versionNumber.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (labTapCount < 6) {
                            labTapCount += 1;
                        }
                        if (labTapCount >= 2 && labTapCount < 6) {
                            Toast.makeText(getActivity(), "Tap " + (6 - labTapCount) + " more times to enable labs", Toast.LENGTH_SHORT).show();
                        }
                        if (labTapCount == 5) {
                            Toast.makeText(getActivity(), "Labs are now enabled. Enjoy!", Toast.LENGTH_LONG).show();
                            prefs.setLabsEnabled(true);
                        }
                        return true;
                    }
                });
            }
            findPreference("about_build_hash").setSummary(BuildConfig.GIT_SHA);
            findPreference("about_device_id").setSummary(Settings.Secure.getString(getActivity().getContentResolver(),
                    Settings.Secure.ANDROID_ID));
        }

        private void setShareOptions(String newValue) {
            PreferenceScreen shareOptions = (PreferenceScreen) findPreference("share_options");
            if (newValue.equals("0")) {
                shareOptions.setEnabled(false);
            } else if (newValue.equals("1")) {
                shareOptions.setEnabled(true);
            }
        }

        // TODO has to be a cleaner way?
        private void setupDeviceOptions() {
            findPreference("dexcom_device_type").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
//                    preference.setSummary(((ListPreference) preference).getEntry());
                    ListPreference listPref = (ListPreference) preference;
                    String summary = listPref.getEntries()[Integer.valueOf((String) newValue)].toString();
                    preference.setSummary(summary);
                    setShareOptions((String) newValue);
                    return true;
                }
            });
        }

        private void setupBarcodeConfigScanner() {
            findPreference("auto_configure").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AndroidBarcode(getActivity()).scan();
                    return true;
                }
            });
        }

        private void setupBtScanner() {
            findPreference("bt_scan_share2").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getActivity().getApplicationContext(), BluetoothScanActivity.class);
                    startActivity(intent);
                    return true;
                }
            });

        }

        private void setupBarcodeShareScanner() {
            findPreference("scan_share2_barcode").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AndroidBarcode(getActivity()).scan();
                    return true;
                }
            });
        }

        @Override
        public void onDetach() {
            super.onDetach();
            try {
                Field childFragmentManager = Fragment.class.getDeclaredField("mChildFragmentManager");
                childFragmentManager.setAccessible(true);
                childFragmentManager.set(this, null);

            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        private void setupValidation() {
            findPreference(PreferenceKeys.API_URIS).setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            String combinedUris = (String) newValue;
                            List<String> splitUris = RestUriUtils.splitIntoMultipleUris(combinedUris);
                            for (String uri : splitUris) {
                                Optional<String> error = PreferencesValidator.validateRestApiUriSyntax(
                                        getActivity(), uri);
                                if (error.isPresent()) {
                                    showValidationError(getActivity(), error.get());
                                    return false;
                                }
                            }
                            return true;
                        }
                    });
            findPreference(PreferenceKeys.MONGO_URI).setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            String mongoUri = (String) newValue;
                            Optional<String> error = PreferencesValidator.validateMongoUriSyntax(
                                    getActivity(), mongoUri);
                            if (error.isPresent()) {
                                showValidationError(getActivity(), error.get());
                                return false;
                            }
                            return true;
                        }
                    });
            findPreference(PreferenceKeys.MQTT_ENDPOINT).setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            String mqttEndpoint = (String) newValue;
                            Optional<String> error = PreferencesValidator.validateMqttEndpointSyntax(
                                    getActivity(), mqttEndpoint);
                            if (error.isPresent()) {
                                showValidationError(getActivity(), error.get());
                                return false;
                            }
                            return true;
                        }
                    });


        }
    }
}


