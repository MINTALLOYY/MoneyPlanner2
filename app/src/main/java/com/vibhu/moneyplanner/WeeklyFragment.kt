package com.vibhu.moneyplanner

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.tooling.data.position
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.vibhu.moneyplanner.categoryexpense.ExpenseData
import com.vibhu.moneyplanner.databinding.FragmentWeeklyGraphBinding
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class WeeklyFragment : Fragment() {

    private lateinit var incomeData: IncomeData
    private lateinit var expenseData: ExpenseData
    private lateinit var initialBalanceData: InitialBalanceData
    private lateinit var binding: FragmentWeeklyGraphBinding
    private var currentBalance: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWeeklyGraphBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        incomeData = IncomeData(requireContext())
        expenseData = ExpenseData(requireContext())
        initialBalanceData = InitialBalanceData(requireContext())

        val sharedPreferences = (requireActivity() as MainActivity).sharedPreferences
        val userId = UUID.fromString(sharedPreferences.getString(SharedPreferencesConstants.USER_ID_PREF, null))
        currentBalance = initialBalanceData.fetchInitialBalance(userId) ?: 0.0
        currentBalance += incomeData.getTotalIncomeAmount() - expenseData.getTotalExpenseAmount()

        val balanceEntries = calculateBalanceEntries(userId)
        setupChart(balanceEntries)
    }

    // Helper data class
    private data class Transaction(
        val amount: Double,
        val date: LocalDate,
        val isIncome: Boolean
    )

    private fun calculateBalanceEntries(userId: UUID): List<Pair<LocalDate, Double>> {
        val weeklyBalances = mutableListOf<Pair<LocalDate, Double>>()
        var runningBalance = initialBalanceData.fetchInitialBalance(userId) ?: 0.0
        Log.d("Initial Balance", runningBalance.toString())

        // Get all transactions sorted by date
        val allTransactions = (incomeData.getAllIncomes().map {
            Transaction(it.amount, it.receivedDate.toLocalDate(), true)
        } + expenseData.getAllExpenses().map {
            Transaction(it.amount, it.expenseDate.toLocalDate(), false)
        }).sortedBy { it.date }

        // Find the earliest Monday on or before first transaction/initial date
        val firstDate = allTransactions.firstOrNull()?.date
            ?: initialBalanceData.fetchInitialDate(userId)?.toLocalDate()
            ?: LocalDate.now()

        var currentWeekStart = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // Process until current week + 1
        val endDate = LocalDate.now().plusWeeks(1)

        // Add initial balance point
        weeklyBalances.add(currentWeekStart to runningBalance)
        Log.d("Balance", "Initial ${currentWeekStart}: $runningBalance")

        while (currentWeekStart.isBefore(endDate)) {
            val currentWeekEnd = currentWeekStart.plusDays(6)

            // Calculate net change for THIS WEEK ONLY
            val weeklyChange = allTransactions
                .filter { it.date in currentWeekStart..currentWeekEnd }
                .sumOf { if(it.isIncome) it.amount else -it.amount }

            runningBalance += weeklyChange

            // Add balance at END of week
            weeklyBalances.add(currentWeekEnd to runningBalance)
            Log.d("Balance", "Week ${currentWeekStart} to $currentWeekEnd: Change=$weeklyChange, New Balance=$runningBalance")

            currentWeekStart = currentWeekStart.plusWeeks(1)
        }

        return weeklyBalances
    }

    private fun setupChart(balanceEntries: List<Pair<LocalDate, Double>>) {
        // Sort entries by date and assign proper x-values
        val sortedEntries = balanceEntries.sortedBy { it.first }
        val dateStrings = sortedEntries.map {
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(it.first.toDate())
        }

        val entries = sortedEntries.mapIndexed { index, (_, balance) ->
            Entry(index.toFloat(), balance.toFloat())
        }

        val dataSet = LineDataSet(entries, "Weekly Balance").apply {
            color = Color.BLUE
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            setDrawValues(false)
        }

        binding.balanceWeekly.apply {

            // 2. Set data and viewport
            data = LineData(dataSet)
            setViewPortOffsets(50f, 20f, 50f, 50f)
            setBackgroundColor(Color.WHITE)

            // 3. Force proper initial layout
            post {
                notifyDataSetChanged()
                invalidate()
            }

            // 4. Control touch behavior
            setTouchEnabled(true)
            setPinchZoom(false)
            setScaleEnabled(false)
        }


        customizeChart(balanceEntries)
    }

    private fun customizeChart(balanceEntries: List<Pair<LocalDate, Double>>) {
        binding.balanceWeekly.apply {

            val shouldHighlightZero = balanceEntries.any { it.second < 0 } &&
                    balanceEntries.any { it.second > 0 }

            // Y-Axis (Left)
            axisLeft.apply {
                setDrawZeroLine(shouldHighlightZero) // Only draw if needed
                if (shouldHighlightZero) {
                    zeroLineWidth = 2.5f  // Bold line
                    zeroLineColor = Color.BLACK  // Visible color
                }

                // Rest of your Y-axis config
                axisMinimum = balanceEntries.minOfOrNull { it.second }?.toFloat()?.minus(100) ?: 0f
                axisMaximum = balanceEntries.maxOfOrNull { it.second }?.toFloat()?.plus(100) ?: 0f
                granularity = 100f
            }

            // X-Axis (Bottom)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelCount = min(5, balanceEntries.size)
                valueFormatter = object : ValueFormatter() {
                    private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt().coerceIn(0, balanceEntries.size - 1)
                        return dateFormat.format(balanceEntries[index].first.toDate())
                    }
                }
                setAvoidFirstLastClipping(true)
                yOffset = 10f
                setAvoidFirstLastClipping(true)
                setDrawGridLines(false)
            }

            // Right Axis
            axisRight.isEnabled = false

            // Legend
            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                yOffset = 25f
                xOffset = 0f
            }

            // General Chart Settings
            setExtraLeftOffset(20f)
            setViewPortOffsets(70f, 30f, 50f, 70f)  // left, top, right, bottom
            description.isEnabled = false
            setDrawGridBackground(false)
            invalidate()
        }
    }

    fun Date.toLocalDate(): LocalDate {
        return this.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    fun LocalDate.toDate(): Date {
        return Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }
}