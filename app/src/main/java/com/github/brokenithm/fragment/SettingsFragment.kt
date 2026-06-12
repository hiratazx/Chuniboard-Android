package com.github.brokenithm.fragment

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.github.brokenithm.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "settings"
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val setDecimalEdit = { editText: EditText -> editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val entries = listOf("fat_touch_threshold", "extra_fat_touch_threshold", "gyro_air_lowest", "gyro_air_highest", "accel_air_threshold", "proximity_air_threshold", "light_air_threshold")
        for (entry in entries)
            findPreference<EditTextPreference>(entry)?.setOnBindEditTextListener(setDecimalEdit)

        findPreference<Preference>("reset_threshold")?.setOnPreferenceClickListener {
            findPreference<SeekBarPreference>("air_line_threshold_int")?.value = 50
            true
        }
    }
}