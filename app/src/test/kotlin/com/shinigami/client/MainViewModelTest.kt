package com.shinigami.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainViewModelTest {

    @Suppress("DEPRECATION")
    @Test
    fun testTriggerManualRefresh_updatesRefreshingStateAndFinishesOnPageFinished() {
        val app = RuntimeEnvironment.getApplication()
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = shadowOf(cm)

        val networkInfo = ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            0,
            true,
            true,
        )
        shadowCm.setActiveNetworkInfo(networkInfo)

        val viewModel = MainViewModel(app)
        ShadowLooper.idleMainLooper()

        viewModel.triggerManualRefresh { }
        assertTrue(viewModel.uiState.value.isRefreshing)

        Thread.sleep(650)
        viewModel.onPageFinished()
        Thread.sleep(650)
        ShadowLooper.idleMainLooper()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}
