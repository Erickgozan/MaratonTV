package com.maratonTv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.maratonTv.data.AppDatabase
import com.maratonTv.data.TvRepository
import com.maratonTv.ui.TvViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun testViewModelInit() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = AppDatabase.getDatabase(context)
    val tvDao = database.tvDao()
    val repository = TvRepository(tvDao, context)
    val viewModel = TvViewModel(context as Application, repository)
    assertNotNull(viewModel)
  }

  @Test
  fun testMainActivityBuild() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      }
    }
  }
}
