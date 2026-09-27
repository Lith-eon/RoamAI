package com.example;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "gemini_prefs";
    public static final String KEY_SHOW_STATUS_INDICATOR = "show_status_indicator";
    public static final String KEY_SHOW_DESCRIPTION_TEXT = "show_description_text";
    public static final String KEY_USE_FEMALE_VOICE = "use_female_voice";
    public static final String KEY_MANUAL_TRIGGER_MODE = "manual_trigger_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Show Status Indicator Preference
        boolean showStatus = prefs.getBoolean(KEY_SHOW_STATUS_INDICATOR, true);
        SwitchMaterial switchShowStatus = findViewById(R.id.switchShowStatus);
        switchShowStatus.setChecked(showStatus);
        switchShowStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_SHOW_STATUS_INDICATOR, isChecked).apply();
        });

        // Show Text Preference
        boolean showText = prefs.getBoolean(KEY_SHOW_DESCRIPTION_TEXT, true);
        SwitchMaterial switchShowText = findViewById(R.id.switchShowText);
        switchShowText.setChecked(showText);
        switchShowText.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_SHOW_DESCRIPTION_TEXT, isChecked).apply();
        });

        // Voice Gender Preference
        boolean isFemale = prefs.getBoolean(KEY_USE_FEMALE_VOICE, false);
        RadioGroup rgVoiceGender = findViewById(R.id.rgVoiceGender);
        RadioButton rbMaleVoice = findViewById(R.id.rbMaleVoice);
        RadioButton rbFemaleVoice = findViewById(R.id.rbFemaleVoice);

        if (isFemale) {
            rbFemaleVoice.setChecked(true);
        } else {
            rbMaleVoice.setChecked(true);
        }

        rgVoiceGender.setOnCheckedChangeListener((group, checkedId) -> {
            boolean useFemale = (checkedId == R.id.rbFemaleVoice);
            prefs.edit().putBoolean(KEY_USE_FEMALE_VOICE, useFemale).apply();
        });

        // Trigger Mode Preference
        boolean isManualMode = prefs.getBoolean(KEY_MANUAL_TRIGGER_MODE, false);
        RadioGroup rgTriggerMode = findViewById(R.id.rgTriggerMode);
        RadioButton rbModeAutomatic = findViewById(R.id.rbModeAutomatic);
        RadioButton rbModeManual = findViewById(R.id.rbModeManual);

        if (isManualMode) {
            rbModeManual.setChecked(true);
        } else {
            rbModeAutomatic.setChecked(true);
        }

        rgTriggerMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean useManual = (checkedId == R.id.rbModeManual);
            prefs.edit().putBoolean(KEY_MANUAL_TRIGGER_MODE, useManual).apply();
        });

        Button showLiscenceButton = findViewById(R.id.showApacheLicence);
        showLiscenceButton.setOnClickListener(v -> {
            showOpenSourceLicenses(this);
        });
    }

    public static boolean isShowStatusIndicatorEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SHOW_STATUS_INDICATOR, true);
    }

    public static boolean isShowDescriptionTextEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SHOW_DESCRIPTION_TEXT, true);
    }

    public static boolean isFemaleVoiceEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_USE_FEMALE_VOICE, false);
    }

    public static boolean isManualTriggerModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_MANUAL_TRIGGER_MODE, false);
    }


    public void showOpenSourceLicenses(Context context) {

        String licenseText = context.getString(R.string.apache_license_text);

        TextView textView = new TextView(context);
        textView.setText(licenseText);
        textView.setTextSize(12f);
        textView.setPadding(32, 24, 32, 24);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(textView);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Apache 2.0 License")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .create();

        dialog.show();
    }

}