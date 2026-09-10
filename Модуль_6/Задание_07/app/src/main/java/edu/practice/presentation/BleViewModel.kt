package edu.practice.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import edu.practice.data.BleRepository

class BleViewModel(app: Application) : AndroidViewModel(app) {
    val repository = BleRepository(app)

    override fun onCleared() {
        repository.close()
    }
}
