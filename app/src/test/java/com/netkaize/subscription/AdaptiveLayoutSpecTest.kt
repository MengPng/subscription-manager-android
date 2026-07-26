package com.netkaize.subscription

import com.netkaize.subscription.ui.AppWindowWidthClass
import com.netkaize.subscription.ui.MinimumTouchTargetDp
import com.netkaize.subscription.ui.adaptiveLayoutSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutSpecTest {
    @Test
    fun compactWidthsUsePhonePaddingAndBottomNavigationClass() {
        val smallest = adaptiveLayoutSpec(320)
        val regular = adaptiveLayoutSpec(412)
        val upperBoundary = adaptiveLayoutSpec(599)

        assertEquals(AppWindowWidthClass.COMPACT, smallest.widthClass)
        assertEquals(16, smallest.pagePaddingDp)
        assertEquals(AppWindowWidthClass.COMPACT, regular.widthClass)
        assertEquals(20, regular.pagePaddingDp)
        assertEquals(AppWindowWidthClass.COMPACT, upperBoundary.widthClass)
    }

    @Test
    fun mediumAndExpandedBreakpointsAreStableAtEdges() {
        assertEquals(AppWindowWidthClass.MEDIUM, adaptiveLayoutSpec(600).widthClass)
        assertEquals(AppWindowWidthClass.MEDIUM, adaptiveLayoutSpec(839).widthClass)
        assertEquals(AppWindowWidthClass.EXPANDED, adaptiveLayoutSpec(840).widthClass)
        assertEquals(760, adaptiveLayoutSpec(720).contentMaxWidthDp)
        assertEquals(1180, adaptiveLayoutSpec(1280).contentMaxWidthDp)
    }

    @Test
    fun interactiveTargetTokenMeetsAndroidAccessibilityMinimum() {
        assertTrue(MinimumTouchTargetDp >= 48)
    }
}
