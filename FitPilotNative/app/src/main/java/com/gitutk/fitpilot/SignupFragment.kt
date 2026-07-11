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

class SignupFragment : Fragment() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignup: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoginLink: TextView
    private lateinit var apiService: ApiService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_signup, container, false)

        apiService = (activity as MainActivity).apiService

        etName = view.findViewById(R.id.etName)
        etEmail = view.findViewById(R.id.etEmail)
        etPassword = view.findViewById(R.id.etPassword)
        btnSignup = view.findViewById(R.id.btnSignup)
        progressBar = view.findViewById(R.id.progressBar)
        tvLoginLink = view.findViewById(R.id.tvLoginLink)

        btnSignup.setOnClickListener {
            performSignup()
        }

        tvLoginLink.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }

    private fun performSignup() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        btnSignup.text = ""
        btnSignup.isEnabled = false
        progressBar.visibility = View.VISIBLE

        apiService.signup(email, password, name) { success, error ->
            activity?.runOnUiThread {
                progressBar.visibility = View.GONE
                btnSignup.isEnabled = true
                btnSignup.text = "Sign Up"

                if (success) {
                    Toast.makeText(context, "Account created! Please log in.", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                } else {
                    Toast.makeText(context, error ?: "Signup failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
