package com.gitutk.fitpilot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class SignupFragment : Fragment() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etWeight: EditText
    private lateinit var etHeight: EditText
    private lateinit var spinnerGender: Spinner
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
        etWeight = view.findViewById(R.id.etWeight)
        etHeight = view.findViewById(R.id.etHeight)
        spinnerGender = view.findViewById(R.id.spinnerGender)
        btnSignup = view.findViewById(R.id.btnSignup)
        progressBar = view.findViewById(R.id.progressBar)
        tvLoginLink = view.findViewById(R.id.tvLoginLink)

        // Setup gender spinner
        val genderOptions = arrayOf("Select Gender", "Male", "Female", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGender.adapter = adapter

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
        val weightStr = etWeight.text.toString().trim()
        val heightStr = etHeight.text.toString().trim()
        val genderPosition = spinnerGender.selectedItemPosition

        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (genderPosition == 0) {
            Toast.makeText(context, "Please select your gender", Toast.LENGTH_SHORT).show()
            return
        }

        if (weightStr.isEmpty() || heightStr.isEmpty()) {
            Toast.makeText(context, "Please enter your weight and height", Toast.LENGTH_SHORT).show()
            return
        }

        val weight = weightStr.toDoubleOrNull()
        val height = heightStr.toDoubleOrNull()

        if (weight == null || weight <= 0 || height == null || height <= 0) {
            Toast.makeText(context, "Please enter valid weight and height values", Toast.LENGTH_SHORT).show()
            return
        }

        val gender = spinnerGender.selectedItem.toString()

        btnSignup.text = ""
        btnSignup.isEnabled = false
        progressBar.visibility = View.VISIBLE

        apiService.signup(email, password, name, weight, height, gender) { success, error ->
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
