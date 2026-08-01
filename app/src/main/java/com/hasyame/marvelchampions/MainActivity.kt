package com.hasyame.marvelchampions

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.hasyame.marvelchampions.core.designsystem.theme.MarvelChampionsTheme
import com.hasyame.marvelchampions.ui.MarvelChampionsApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity. It extends [AppCompatActivity] rather than ComponentActivity
 * because per-app language selection goes through
 * `AppCompatDelegate.setApplicationLocales`, which needs the AppCompat delegate to
 * apply without an app restart below API 33.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MarvelChampionsTheme {
                MarvelChampionsApp()
            }
        }
    }
}
