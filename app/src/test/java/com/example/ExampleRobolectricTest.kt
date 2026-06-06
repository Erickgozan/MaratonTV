package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TvRepository
import com.example.ui.TvViewModel
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
