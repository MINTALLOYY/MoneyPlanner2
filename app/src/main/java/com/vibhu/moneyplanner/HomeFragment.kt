package com.vibhu.moneyplanner

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import java.util.Calendar
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.add
import androidx.compose.ui.tooling.data.position
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.add
import androidx.recyclerview.widget.LinearLayoutManager
import com.vibhu.moneyplanner.categoryexpense.ExpenseData
import com.vibhu.moneyplanner.databinding.FragmentHomeBinding
import com.vibhu.moneyplanner.models.InitialBalance
import com.vibhu.moneyplanner.models.Transaction
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.text.format
import kotlin.text.toFloat
import kotlin.text.withIndex
import androidx.core.view.isVisible

class HomeFragment: Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var incomeData: IncomeData
    private lateinit var expenseData: ExpenseData
    private lateinit var initialBalanceData: InitialBalanceData
    private var currentBalance: Double = 0.0
    private lateinit var transactionHistoryList: List<Transaction>
    private var initialBalance: Double? = 0.0
    private lateinit var transactionAdapter: TransactionHistoryAdapter
    private lateinit var transactionData: TransactionData

    private lateinit var searchListAdapter: SearchListAdapter
    private var currentFilterType = FilterType.ALL
    private var startDate: Date? = null
    private var endDate: Date? = null
    enum class FilterType { ALL, INCOME, EXPENSE }

    private val searchTextListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?) = true

        override fun onQueryTextChange(newText: String?): Boolean {
            if (newText.isNullOrEmpty()) {
                hideSearchResults()
            } else {
                showSearchResults()
                applyFilters()
            }
            return true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        incomeData = IncomeData(requireContext())
        expenseData = ExpenseData(requireContext())
        initialBalanceData = InitialBalanceData(requireContext())
        transactionData = TransactionData(requireContext())

        binding.transactionHistory.layoutManager = LinearLayoutManager(requireContext())

        val searchView = binding.searchTransaction
        searchView.clearFocus()

        val userUUIDStr = (requireActivity() as MainActivity).sharedPreferences.getString(SharedPreferencesConstants.USER_ID_PREF, null)
        Log.d("userUUIDStr", "" + userUUIDStr)
        val balance = initialBalanceData.fetchInitialBalance(UUID.fromString(userUUIDStr))!!
        Log.d("balance", "" + balance)

        initialBalance = 0.0 + balance

        var sharedPreferences = (requireActivity() as MainActivity).sharedPreferences
        var userId = UUID.fromString(sharedPreferences.getString(SharedPreferencesConstants.USER_ID_PREF, null))

        getCurrentBalance()
        binding.currentBalance.text = currentBalance.toString()
        Log.d("currentBalance", "" + currentBalance)

        getTransactionHistory()

        transactionAdapter = TransactionHistoryAdapter(
            transactionHistoryList,
            requireContext(),
            { transaction ->
                navigateToTransactionDetails(transaction)
            }
        )

        binding.transactionHistory.adapter = transactionAdapter
        transactionAdapter.updateItems(transactionData.getAllTransaction())

        searchListAdapter = SearchListAdapter(
            emptyList(),
            requireContext()
        ) { transaction ->
            navigateToTransactionDetails(transaction)
            hideSearchResults()
            binding.searchTransaction.clearFocus()
            hideKeyboard()

        }
        setupSearch()
        setupFiltering()

        setFragment(WeeklyFragment())
        binding.weeklyMonthlyChanger.setOnClickListener{
            changeFragment()
        }

        val message = arguments?.getString("message")
        if (message != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearch() {
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchListAdapter
        }

        // Handle search queries
        binding.searchTransaction.setOnQueryTextListener(searchTextListener)

        // Handle click outside
        binding.rootLayout.setOnClickListener {
            Log.e("Root Click", "Root has been clicked")
            if (binding.searchResultsRecyclerView.isVisible || binding.filterDropdown.isVisible) {
                hideSearchResults()
                hideFilters()
                Log.e("Root Click", "Search Results RecyclerView and Filters are Hidden")
            }
        }
    }

    private fun setupFiltering() {
        // Show/hide filter button based on search focus
        binding.searchTransaction.setOnQueryTextFocusChangeListener { _, hasFocus ->
            binding.filterButton.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }

        // Toggle filter dropdown
        binding.filterButton.setOnClickListener {
            if (binding.filterDropdown.visibility == View.VISIBLE) {
                binding.filterDropdown.visibility = View.GONE
            } else {
                binding.filterDropdown.visibility = View.VISIBLE
                // Set current selection
                when (currentFilterType) {
                    FilterType.ALL -> binding.filterAll.isChecked = true
                    FilterType.INCOME -> binding.filterIncome.isChecked = true
                    FilterType.EXPENSE -> binding.filterExpense.isChecked = true
                }
            }
        }

        // Handle filter type selection
        binding.filterTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilterType = when (checkedId) {
                R.id.filterIncome -> FilterType.INCOME
                R.id.filterExpense -> FilterType.EXPENSE
                else -> FilterType.ALL
            }
        }

        // Date range picker
        binding.btnDateRange.setOnClickListener {
            showDateRangePicker()
        }

        // Apply filters
        binding.btnApplyFilters.setOnClickListener {
            applyFilters()
            binding.filterDropdown.visibility = View.GONE
        }
    }

    private fun showDateRangePicker() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.date_range_dialog, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val btnStartDate = dialogView.findViewById<Button>(R.id.btnStartDate)
        val btnEndDate = dialogView.findViewById<Button>(R.id.btnEndDate)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDates)

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault() // Add this for consistency
        }

        btnStartDate.text = startDate?.let { dateFormat.format(it) } ?: "Select start date"
        btnEndDate.text = endDate?.let { dateFormat.format(it) } ?: "Select end date"

        btnStartDate.setOnClickListener {
            val datePicker = DatePickerDialog(requireContext(), { _, year, month, day ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                startDate = cal.time
                btnStartDate.text = dateFormat.format(cal.time)
            }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH),
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        btnEndDate.setOnClickListener {
            val datePicker = DatePickerDialog(requireContext(), { _, year, month, day ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                endDate = cal.time
                btnEndDate.text = dateFormat.format(cal.time)
            }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH),
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyFilters() {
        val query = binding.searchTransaction.query.toString()
        val filtered = transactionData.getAllTransaction().filter { transaction ->
            // Apply search query filter
            val matchesQuery = query.isEmpty() ||
                    transaction.transactionName.contains(query, ignoreCase = true)

            // Apply type filter
            val matchesType = when (currentFilterType) {
                FilterType.ALL -> true
                FilterType.INCOME -> transaction.isIncome
                FilterType.EXPENSE -> !transaction.isIncome
            }

            // Apply date filter
            val matchesDate = when {
                startDate == null && endDate == null -> true
                startDate != null && endDate != null -> transaction.date in startDate!!..endDate!!
                startDate != null -> transaction.date >= startDate
                else -> transaction.date <= endDate!!
            }

            matchesQuery && matchesType && matchesDate
        }

        searchListAdapter.updateItems(filtered)
    }

    private fun showSearchResults() {
        binding.searchResultsRecyclerView.visibility = View.VISIBLE
        // Add fade animation
        binding.searchResultsRecyclerView.alpha = 0f
        binding.searchResultsRecyclerView.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun hideKeyboard() {
        val activity = requireActivity()
        val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = activity.currentFocus ?: binding.root
        inputMethodManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        binding.searchTransaction.clearFocus()
    }

    private fun hideSearchResults() {
        binding.searchResultsRecyclerView.visibility = View.GONE
        binding.searchTransaction.clearFocus()
        binding.searchTransaction.setOnQueryTextListener(null) // Temporarily remove listener
        binding.searchTransaction.setQuery("", false)
        binding.searchTransaction.setOnQueryTextListener(searchTextListener) // Restore listener


        hideKeyboard()
        Log.e("hideSearchResults", "hideSearchResults called")

    }

    private fun setFragment(graphFragment: Fragment){
        val fragmentManager = requireActivity().supportFragmentManager
        val transaction: FragmentTransaction = fragmentManager.beginTransaction()
        transaction.replace(binding.balanceChart.id, graphFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun changeFragment(){
        if(binding.weeklyMonthlyChanger.text == "Change to Monthly"){
            setFragment(MonthlyFragment())
            binding.weeklyMonthlyChanger.text = "Change to Weekly"
        }
        else{
            setFragment(WeeklyFragment())
            binding.weeklyMonthlyChanger.text = "Change to Monthly"
        }
    }

    private fun getCurrentBalance(): Double{
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_WEEK, -7)
        val lastWeek = cal.time

        // val initial = initialBalanceData.fetchInitialDate(UUID.fromString((requireActivity() as MainActivity).sharedPreferences.getString(SharedPreferencesConstants.USER_ID_PREF, null)))!!

        val incomeBalanceList = incomeData.getIncomesInDateRange(lastWeek, Date())
        val expenseBalanceList = expenseData.getExpensesInDateRange(lastWeek, Date())

        var incomeBalance = 0.0
        var expenseBalance = 0.0
        if(incomeBalanceList.isNotEmpty() && expenseBalanceList.isNotEmpty()){
            for(income in incomeBalanceList){
                incomeBalance += income.amount
            }
            for(expense in expenseBalanceList){
                expenseBalance += expense.amount
            }
            Log.d("expenseBalance", expenseBalance.toString())
            Log.d("incomeBalance", incomeBalance.toString())
            currentBalance = incomeBalance - expenseBalance + initialBalance!!
        }
        else{
            currentBalance = 0.0 + initialBalance!!
        }
        return currentBalance

    }

    private fun hideFilters() {
        binding.filterDropdown.visibility = View.GONE
    }

    private fun getTransactionHistory(): List<Transaction>{

        val allTransactions = transactionData.getAllTransaction()

        transactionHistoryList = allTransactions

        return allTransactions
    }

    private fun navigateToTransactionDetails(transaction: Transaction){
        val bundle = Bundle()
        bundle.putString("transaction_id", transaction.id.toString())

        val fragmentManager = requireActivity().supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()

        val transactionDetailsFragment = TransactionDetailsFragment()
        transactionDetailsFragment.arguments = bundle

        fragmentTransaction.replace(R.id.fragment_container, transactionDetailsFragment)
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        expenseData.close()
        incomeData.close()
        initialBalanceData.close()
    }

}