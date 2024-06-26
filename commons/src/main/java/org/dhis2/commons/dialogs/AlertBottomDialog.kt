package org.dhis2.commons.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.dhis2.commons.R
import org.dhis2.commons.databinding.AlertBottomDialogBinding

class AlertBottomDialog : BottomSheetDialogFragment() {

    companion object {
        val instance: AlertBottomDialog
            get() = AlertBottomDialog()
    }

    fun setTitle(title: String) = apply { this.title = title }

    fun setMessage(message: String) = apply { this.message = message }

    fun setPositiveButton(text: String? = null, onClick: (() -> Unit)? = null) = apply {
        this.positiveText = text
        this.positiveOnclick = onClick
    }

    fun setNegativeButton(text: String? = null, onClick: (() -> Unit)? = null) = apply {
        this.showNegative = true
        this.negativeText = text
        this.negativeOnclick = onClick
    }

    fun setFieldsToDisplay(fieldsToDisplay: List<String>) = apply {
        this.fieldsToDisplay = fieldsToDisplay
    }

    private var fieldsToDisplay: List<String>? = null
    private var showNegative: Boolean = false
    private var positiveText: String? = null
    private var positiveOnclick: (() -> Unit)? = null
    private var negativeText: String? = null
    private var negativeOnclick: (() -> Unit)? = null
    private lateinit var binding: AlertBottomDialogBinding
    private var title: String? = null
    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, org.dhis2.ui.R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.alert_bottom_dialog, container, false)
        binding.setTitle(title)
        binding.setMessage(message)

        val colorRes = ContextCompat.getColor(binding.root.context, R.color.colorPrimary)

        binding.positive.apply {
            setTextColor(colorRes)
            positiveText?.let { text = it }
            setOnClickListener {
                when (positiveOnclick) {
                    null -> dismiss()
                    else -> {
                        positiveOnclick!!.invoke()
                        dismiss()
                    }
                }
            }
        }
        binding.negative.apply {
            setTextColor(colorRes)
            if (showNegative) {
                visibility = View.VISIBLE
            }
            negativeText?.let { text = it }
            setOnClickListener {
                when (negativeOnclick) {
                    null -> dismiss()
                    else -> {
                        negativeOnclick!!.invoke()
                        dismiss()
                    }
                }
            }
        }

        fieldsToDisplay?.let { showFields() }

        return binding.root
    }

    // This is necessary to show the bottomSheet dialog with full height on landscape
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val dialog = dialog as BottomSheetDialog

            val bottomSheet =
                dialog.findViewById<FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet,
                )
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = 0
        }
    }

    private fun showFields() {
        var fields = ""
        fieldsToDisplay?.forEach { field -> fields += "$field\n" }

        binding.emptyMandatoryFields.apply {
            visibility = View.VISIBLE
            text = fields
        }
    }
}
