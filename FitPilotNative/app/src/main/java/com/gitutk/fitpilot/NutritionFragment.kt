package com.gitutk.fitpilot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NutritionFragment : Fragment() {

    private lateinit var apiService: ApiService
    private lateinit var rvNutrition: RecyclerView
    private lateinit var adapter: NutritionAdapter

    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterProtein: Button
    private lateinit var btnFilterFats: Button
    private lateinit var btnFilterCarbs: Button

    private val allFoods = listOf(
        NutritionItem(
            name = "Eggs/Egg Scramble",
            category = "HIGH PROTEIN",
            serving = "Serving: 3 scrambled eggs (150g)",
            description = "Soft, fluffy scrambled eggs cooked in a pan with a touch of butter. The ultimate quick protein source for busy study nights.",
            protein = 18.0,
            carbs = 2.0,
            fat = 15.0,
            calories = 220.0,
            imageResId = R.drawable.egg_scramble
        ),
        NutritionItem(
            name = "Moong Dal Chilla",
            category = "COMPLEX CARBS",
            serving = "Serving: 2 pancakes (120g)",
            description = "Savoury pancakes made from split yellow moong dal paste. Packed with digestive dietary fiber and light plant proteins.",
            protein = 14.0,
            carbs = 28.0,
            fat = 8.0,
            calories = 250.0,
            imageResId = R.drawable.moong_dal_chilla
        ),
        NutritionItem(
            name = "Bread Peanut Butter",
            category = "HEALTHY FATS",
            serving = "Serving: 2 slices (80g)",
            description = "Toasted whole wheat bread spread with creamy peanut butter. Quick, cheap, and calorie-dense for busy college students.",
            protein = 12.0,
            carbs = 24.0,
            fat = 16.0,
            calories = 290.0,
            imageResId = R.drawable.bread_peanut_butter
        ),
        NutritionItem(
            name = "Soya Chunks Curry",
            category = "HIGH PROTEIN",
            serving = "Serving: 1 bowl (200g)",
            description = "Curry cooked with textured soy protein chunks. Extremely high plant-based protein content with zero saturated fat.",
            protein = 25.0,
            carbs = 12.0,
            fat = 4.0,
            calories = 180.0,
            imageResId = R.drawable.soya_chunks
        ),
        NutritionItem(
            name = "Moong Sprouts Salad",
            category = "COMPLEX CARBS",
            serving = "Serving: 1 bowl (150g)",
            description = "Sprouted green gram salad mixed with chopped cucumber, tomato, lemon juice, and chaat masala. Rich in live enzymes and vitamins.",
            protein = 8.0,
            carbs = 22.0,
            fat = 1.0,
            calories = 130.0,
            imageResId = R.drawable.moong_sprouts
        ),
        NutritionItem(
            name = "Masala Dahi / Greek Yogurt",
            category = "HIGH PROTEIN",
            serving = "Serving: 1 bowl (180g)",
            description = "Creamy dahi or Greek yogurt topped with roasted peanuts, cumin, and black salt. Excellent for digestion and muscle recovery.",
            protein = 18.0,
            carbs = 10.0,
            fat = 10.0,
            calories = 200.0,
            imageResId = R.drawable.greek_yogurt
        ),
        NutritionItem(
            name = "Oatmeal Bowl",
            category = "COMPLEX CARBS",
            serving = "Serving: 1 bowl (150g)",
            description = "Whole rolled oats cooked in low fat milk, providing beta-glucan fiber which helps sustain stable blood sugar levels.",
            protein = 6.0,
            carbs = 32.0,
            fat = 5.0,
            calories = 200.0,
            imageResId = R.drawable.oatmeal
        ),
        NutritionItem(
            name = "Grilled Chicken Salad",
            category = "HIGH PROTEIN",
            serving = "Serving: 1 plate (200g)",
            description = "Lean grilled breast of chicken strips tossed with crisp garden greens and vinaigrette. Cleanest post-workout repair protein.",
            protein = 30.0,
            carbs = 8.0,
            fat = 6.0,
            calories = 220.0,
            imageResId = R.drawable.chicken_salad
        ),
        NutritionItem(
            name = "Pan-Seared Salmon",
            category = "HEALTHY FATS",
            serving = "Serving: 1 fillet (150g)",
            description = "Crispy pan-seared fresh salmon fillet rich in Omega-3 fatty acids, supporting brain function and lowering inflammation.",
            protein = 24.0,
            carbs = 0.0,
            fat = 14.0,
            calories = 230.0,
            imageResId = R.drawable.salmon
        ),
        NutritionItem(
            name = "Mixed Almonds & Nuts",
            category = "HEALTHY FATS",
            serving = "Serving: 1 handful (30g)",
            description = "A raw mix of almonds, walnuts, and cashews. Dense source of healthy polyunsaturated fats, vitamin E, and minerals.",
            protein = 6.0,
            carbs = 6.0,
            fat = 15.0,
            calories = 180.0,
            imageResId = R.drawable.almonds
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_nutrition, container, false)

        apiService = (activity as MainActivity).apiService
        rvNutrition = view.findViewById(R.id.rvNutrition)
        
        btnFilterAll = view.findViewById(R.id.btnFilterAll)
        btnFilterProtein = view.findViewById(R.id.btnFilterProtein)
        btnFilterFats = view.findViewById(R.id.btnFilterFats)
        btnFilterCarbs = view.findViewById(R.id.btnFilterCarbs)

        rvNutrition.layoutManager = LinearLayoutManager(context)
        adapter = NutritionAdapter(allFoods) { item ->
            quickLogMeal(item)
        }
        rvNutrition.adapter = adapter

        setupFilters()

        return view
    }

    private fun setupFilters() {
        btnFilterAll.setOnClickListener {
            updateFilterSelection(btnFilterAll)
            adapter.updateList(allFoods)
        }
        btnFilterProtein.setOnClickListener {
            updateFilterSelection(btnFilterProtein)
            adapter.updateList(allFoods.filter { it.category == "HIGH PROTEIN" })
        }
        btnFilterFats.setOnClickListener {
            updateFilterSelection(btnFilterFats)
            adapter.updateList(allFoods.filter { it.category == "HEALTHY FATS" })
        }
        btnFilterCarbs.setOnClickListener {
            updateFilterSelection(btnFilterCarbs)
            adapter.updateList(allFoods.filter { it.category == "COMPLEX CARBS" })
        }
    }

    private fun updateFilterSelection(selectedButton: Button) {
        val buttons = listOf(btnFilterAll, btnFilterProtein, btnFilterFats, btnFilterCarbs)
        val activeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary)
        val inactiveColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary)

        buttons.forEach { btn ->
            if (btn == selectedButton) {
                btn.setTextColor(activeColor)
                btn.setTextAppearance(android.R.style.TextAppearance_Small) // Bold simulation
            } else {
                btn.setTextColor(inactiveColor)
            }
        }
    }

    private fun quickLogMeal(item: NutritionItem) {
        Toast.makeText(context, "Logging ${item.name}...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val (success, _, error) = apiService.logMeal(
                description = item.name,
                calories = item.calories,
                protein = item.protein,
                carbs = item.carbs,
                fat = item.fat
            )
            if (success) {
                Toast.makeText(context, "${item.name} added to today's logged meals!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, error ?: "Failed to log meal", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Nutrition Adapter & ViewHolder Implementation
    private class NutritionAdapter(
        private var list: List<NutritionItem>,
        private val onLogClicked: (NutritionItem) -> Unit
    ) : RecyclerView.Adapter<NutritionAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ivFoodImage: ImageView = v.findViewById(R.id.ivFoodImage)
            val tvFoodName: TextView = v.findViewById(R.id.tvFoodName)
            val tvFoodCategory: TextView = v.findViewById(R.id.tvFoodCategory)
            val tvServing: TextView = v.findViewById(R.id.tvServing)
            val tvDescription: TextView = v.findViewById(R.id.tvDescription)
            val tvProtein: TextView = v.findViewById(R.id.tvProtein)
            val tvCarbs: TextView = v.findViewById(R.id.tvCarbs)
            val tvFat: TextView = v.findViewById(R.id.tvFat)
            val tvCalories: TextView = v.findViewById(R.id.tvCalories)
            val btnQuickLog: Button = v.findViewById(R.id.btnQuickLogMeal)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nutrition_food, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.ivFoodImage.setImageResource(item.imageResId)
            holder.tvFoodName.text = item.name
            holder.tvFoodCategory.text = item.category
            holder.tvServing.text = item.serving
            holder.tvDescription.text = item.description
            holder.tvProtein.text = "${item.protein.toInt()}g"
            holder.tvCarbs.text = "${item.carbs.toInt()}g"
            holder.tvFat.text = "${item.fat.toInt()}g"
            holder.tvCalories.text = "${item.calories.toInt()} kcal"

            holder.btnQuickLog.setOnClickListener {
                onLogClicked(item)
            }

            // Stylistic tinting of categories
            val context = holder.itemView.context
            when (item.category) {
                "HIGH PROTEIN" -> {
                    holder.tvFoodCategory.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))
                    holder.tvFoodCategory.setBackgroundColor(android.graphics.Color.parseColor("#EFF6FF"))
                }
                "HEALTHY FATS" -> {
                    holder.tvFoodCategory.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                    holder.tvFoodCategory.setBackgroundColor(android.graphics.Color.parseColor("#F0FDF4"))
                }
                "COMPLEX CARBS" -> {
                    holder.tvFoodCategory.setTextColor(android.graphics.Color.parseColor("#D97706"))
                    holder.tvFoodCategory.setBackgroundColor(android.graphics.Color.parseColor("#FEF3C7"))
                }
            }
        }

        override fun getItemCount() = list.size

        fun updateList(newList: List<NutritionItem>) {
            this.list = newList
            notifyDataSetChanged()
        }
    }

    data class NutritionItem(
        val name: String,
        val category: String,
        val serving: String,
        val description: String,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val calories: Double,
        val imageResId: Int
    )
}
