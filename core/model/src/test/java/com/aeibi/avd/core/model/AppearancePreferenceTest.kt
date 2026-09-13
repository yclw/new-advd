package com.aeibi.avd.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearancePreferenceTest {
    @Test
    fun `maps AppCompat language tags to supported app languages`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTag("zh-Hans"))
    }

    @Test
    fun `does not accept unsupported language tags`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("zh-CN"))
    }
}
