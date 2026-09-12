package com.aeibi.avd.core.navigation

interface NavKey

interface Navigator {
    fun navigate(key: NavKey)
    fun goBack()
}
