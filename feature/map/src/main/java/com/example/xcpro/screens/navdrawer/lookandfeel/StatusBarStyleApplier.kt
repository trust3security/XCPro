package com.example.xcpro.screens.navdrawer.lookandfeel

/**
 * Simple interface implemented by host activities that can adjust the status bar appearance
 * when the user changes Look & Feel preferences within the map feature.
 */
interface StatusBarStyleApplier {
    fun applyUserStatusBarStyle(profileId: String?)
}
