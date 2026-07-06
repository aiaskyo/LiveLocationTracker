package com.example.livelocationtracker.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.livelocationtracker.databinding.ActivityLoginBinding
import com.example.livelocationtracker.ui.permission.PermissionActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Simple Material 3 sign-in screen: email/password sign-in, registration,
 * and an anonymous sign-in shortcut for quick testing/demo purposes.
 * On success, routes to PermissionActivity, which decides whether the
 * runtime permission flow or the map screen comes next.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSignIn.setOnClickListener {
            viewModel.signIn(
                binding.editEmail.text.toString(),
                binding.editPassword.text.toString()
            )
        }

        binding.buttonRegister.setOnClickListener {
            viewModel.register(
                binding.editEmail.text.toString(),
                binding.editPassword.text.toString()
            )
        }

        binding.buttonAnonymous.setOnClickListener {
            viewModel.signInAnonymously()
        }

        observeUiState()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is AuthUiState.Idle -> setLoading(false)
                    is AuthUiState.Loading -> setLoading(true)
                    is AuthUiState.Success -> {
                        setLoading(false)
                        startActivity(Intent(this@LoginActivity, PermissionActivity::class.java))
                        finish()
                    }
                    is AuthUiState.Error -> {
                        setLoading(false)
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        binding.buttonSignIn.isEnabled = !isLoading
        binding.buttonRegister.isEnabled = !isLoading
        binding.buttonAnonymous.isEnabled = !isLoading
    }
}
