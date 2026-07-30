package com.gitutk.fitpilot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvRegisterLink: TextView
    private lateinit var apiService: ApiService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        apiService = (activity as MainActivity).apiService

        etEmail = view.findViewById(R.id.etEmail)
        etPassword = view.findViewById(R.id.etPassword)
        btnLogin = view.findViewById(R.id.btnLogin)
        progressBar = view.findViewById(R.id.progressBar)
        tvRegisterLink = view.findViewById(R.id.tvRegisterLink)

        btnLogin.setOnClickListener {
            performLogin()
        }

        tvRegisterLink.setOnClickListener {
            (activity as MainActivity).loadFragment(SignupFragment(), true)
        }

        return view
    }

    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        btnLogin.text = ""
        btnLogin.isEnabled = false
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val (success, error) = apiService.login(email, password)
            progressBar.visibility = View.GONE
            btnLogin.isEnabled = true
            btnLogin.text = "Log In"

            if (success) {
                (activity as MainActivity).showMainApp()
            } else {
                Toast.makeText(context, error ?: "Login failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
