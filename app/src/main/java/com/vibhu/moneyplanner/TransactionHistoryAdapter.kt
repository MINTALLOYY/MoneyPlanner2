package com.vibhu.moneyplanner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.vibhu.moneyplanner.models.Transaction
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionHistoryAdapter(
    private var transactions: List<Transaction>,
    private val context: Context,
    private val onItemClick: (Transaction) -> Unit ,
) : RecyclerView.Adapter<TransactionHistoryAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.textViewTransactionName)
        val amountTextView: TextView = itemView.findViewById(R.id.textViewTransactionAmount)
        val dateTextView: TextView = itemView.findViewById(R.id.textViewTransactionDate)
        val representationOfExpenseIncomeTop: View = itemView.findViewById(R.id.representationOfExpenseIncomeTop)
        val representationOfExpenseIncomeBottom: View = itemView.findViewById(R.id.representationOfExpenseIncomeBottom)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        holder.nameTextView.text = transaction.transactionName

        if(transaction.isIncome){
            holder.amountTextView.text = "+ $" + transaction.amount
            holder.representationOfExpenseIncomeTop.background = ContextCompat.getDrawable(context, R.color.green_text)
            holder.representationOfExpenseIncomeBottom.background = ContextCompat.getDrawable(context, R.color.green_text)
        }
        else{
            holder.amountTextView.text = "- $" + transaction.amount
            holder.representationOfExpenseIncomeTop.background = ContextCompat.getDrawable(context, R.color.red)
            holder.representationOfExpenseIncomeBottom.background = ContextCompat.getDrawable(context, R.color.red)
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedDate = dateFormat.format(transaction.date)
        holder.dateTextView.text = formattedDate

        holder.itemView.setOnClickListener{
            onItemClick(transaction)
        }
    }

    override fun getItemCount(): Int {
        return transactions.size
    }

    fun updateItems(newTransactions: List<Transaction>) {
        this.transactions = newTransactions
        notifyDataSetChanged()
    }
}