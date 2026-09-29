package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.util.ShareProgressData

/** Transient presentation state: retain the prepared card across Activity recreation.
 * No Activity/context, patient persistence, saved-state bitmap or regeneration.
 * Process death intentionally starts with no preview; closing releases this reference.
 */
internal class PatientSharePreviewViewModel : ViewModel() {
    var data by mutableStateOf<ShareProgressData?>(null)
        private set

    fun open(prepared: ShareProgressData) { data = prepared }
    fun dismiss() { data = null }

    override fun onCleared() { data = null }
}
