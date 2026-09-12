package com.aeibi.avd.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.avd.core.designsystem.AvdTheme
import com.aeibi.avd.core.model.ThemePreference
import com.aeibi.avd.domain.settings.ObserveThemePreferenceUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var observeThemePreference: ObserveThemePreferenceUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themePreference = observeThemePreference()
                .collectAsStateWithLifecycle(initialValue = ThemePreference())
                .value
            AvdTheme(preference = themePreference) {
                AppRoot()
            }
        }
    }
}
