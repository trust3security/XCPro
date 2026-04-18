package com.trust3.xcpro.livefollow.watch

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.trust3.xcpro.livefollow.LiveFollowRoutes
import com.trust3.xcpro.livefollow.normalizeLiveFollowShareCode
import com.trust3.xcpro.livefollow.normalizeLiveFollowSessionId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LiveFollowWatchEntryRoutePolicyTest {

    @Test
    fun normalizeLiveFollowSessionId_returnsNullForBlankInput() {
        assertNull(normalizeLiveFollowSessionId("   "))
        assertEquals("watch-1", normalizeLiveFollowSessionId(" watch-1 "))
    }

    @Test
    fun normalizeLiveFollowShareCode_uppercasesAndRejectsInvalidShape() {
        assertEquals("WATCH123", normalizeLiveFollowShareCode(" watch123 "))
        assertNull(normalizeLiveFollowShareCode("watch-123"))
        assertNull(normalizeLiveFollowShareCode("short"))
    }

    @Test
    fun handoffLiveFollowWatchToMap_popsToExistingMapRoute() = runTest {
        val navController: NavHostController = mock()
        whenever(navController.popBackStack(LiveFollowRoutes.MAP_ROUTE, false)).thenReturn(true)

        handoffLiveFollowWatchToMap(navController)

        verify(navController).popBackStack(LiveFollowRoutes.MAP_ROUTE, false)
        verify(navController, never()).navigate(
            eq(LiveFollowRoutes.MAP_ROUTE),
            any<NavOptionsBuilder.() -> Unit>()
        )
    }

    @Test
    fun handoffLiveFollowWatchToMap_navigatesWhenMapRouteMissing() = runTest {
        val navController: NavHostController = mock()
        whenever(navController.popBackStack(LiveFollowRoutes.MAP_ROUTE, false)).thenReturn(false)

        handoffLiveFollowWatchToMap(navController)

        verify(navController).popBackStack(LiveFollowRoutes.MAP_ROUTE, false)
        verify(navController).navigate(
            eq(LiveFollowRoutes.MAP_ROUTE),
            any<NavOptionsBuilder.() -> Unit>()
        )
    }
}
