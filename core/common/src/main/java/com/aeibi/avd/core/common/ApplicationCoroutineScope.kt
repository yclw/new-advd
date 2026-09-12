package com.aeibi.avd.core.common

import javax.inject.Qualifier

/** Execution host for process-lifetime work; it does not grant business ownership. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationCoroutineScope
