package org.dhis2.form.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMarginsRelative
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.ListAdapter
import org.dhis2.commons.bindings.RoundedCornerMode
import org.dhis2.commons.bindings.clipWithRoundedCorners
import org.dhis2.commons.bindings.dp
import org.dhis2.commons.data.EventViewModelType
import org.dhis2.form.R
import org.dhis2.form.model.FieldUiModel
import org.dhis2.form.model.SectionUiModelImpl
import org.dhis2.form.ui.FormViewHolder.FieldItemCallback
import org.dhis2.form.ui.event.RecyclerViewUiEvents
import org.dhis2.form.ui.intent.FormIntent
import org.hisp.dhis.android.core.common.ValueType

class DataEntryAdapter(private val searchStyle: Boolean) :
    ListAdapter<FieldUiModel, FormViewHolder>(DataEntryDiff()),
    FieldItemCallback {

    var onIntent: ((intent: FormIntent) -> Unit)? = null
    var onRecyclerViewUiEvents: ((uiEvent: RecyclerViewUiEvents) -> Unit)? = null

    private val sectionHandler = SectionHandler()
    var sectionPositions: MutableMap<String, Int> = LinkedHashMap()

    val textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            // Not needed
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            currentList.find { it.focused }?.onTextChange(p0)
        }

        override fun afterTextChanged(p0: Editable?) {
            // Not needed
        }
    }

    val coordinateWatcher = LatitudeLongitudeTextWatcher { coordinates ->
        currentList.find { it.focused && it.valueType == ValueType.COORDINATE }
            ?.onTextChange(coordinates)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormViewHolder {
        val layoutInflater =
            if (searchStyle) {
                LayoutInflater.from(
                    ContextThemeWrapper(
                        parent.context,
                        R.style.searchFormInputText
                    )
                )
            } else {
                LayoutInflater.from(
                    ContextThemeWrapper(
                        parent.context,
                        R.style.formInputText
                    )
                )
            }
        val binding =
            DataBindingUtil.inflate<ViewDataBinding>(layoutInflater, viewType, parent, false)
        return FormViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FormViewHolder, position: Int) {
        if (getItem(position) is SectionUiModelImpl) {
            updateSectionData(position, false)
        }
        holder.bind(getItem(position), this, textWatcher, coordinateWatcher)
        updateMargins(holder)
    }

    private fun updateMargins(holder: FormViewHolder) {
        val (margin, bgColor) = if (
            searchStyle
            || getItem(holder.bindingAdapterPosition) is SectionUiModelImpl
            || currentList.find { it is SectionUiModelImpl } == null
        ) {
            Pair(0.dp, R.color.zxing_transparent)
        } else {
            Pair(16.dp, R.color.form_field_background)
        }
        val radius = if (searchStyle || getItem(holder.bindingAdapterPosition) is SectionUiModelImpl) {
            0.dp
        } else {
            8.dp
        }
        val mode = when {
            isSection(holder.bindingAdapterPosition) ->
                RoundedCornerMode.NONE
            shouldDisplayAllRoundedCorners(holder.bindingAdapterPosition) ->
                RoundedCornerMode.ALL
            shouldDisplayTopRoundedCorners(holder.bindingAdapterPosition) ->
                RoundedCornerMode.TOP
            shouldDisplayBottomRoundedCorners(holder.bindingAdapterPosition) ->
                RoundedCornerMode.BOTTOM
            else ->
                RoundedCornerMode.NONE
        }

        holder.itemView.clipWithRoundedCorners(curvedRadio = radius, mode = mode)
        holder.itemView.setBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, bgColor)
        )
        holder.itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            this.updateMarginsRelative(start = margin, end = margin)
        }
    }

    private fun shouldDisplayTopRoundedCorners(holderPosition: Int): Boolean {
        val prevPosition = holderPosition - 1
        val nextPosition = holderPosition + 1
        val isAfterSection = prevPosition.takeIf { it >= 0 }?.let {
            isSection(prevPosition)
        } ?: false
        val isBeforeField = nextPosition.takeIf { it < itemCount }?.let {
            !isSection(nextPosition)
        } ?: false
        return isAfterSection and isBeforeField
    }

    private fun shouldDisplayBottomRoundedCorners(holderPosition: Int): Boolean {
        val prevPosition = holderPosition - 1
        val nextPosition = holderPosition + 1
        val isAfterField = prevPosition.takeIf { it >= 0 }?.let {
            !isSection(prevPosition)
        } ?: false
        val isBeforeSection = nextPosition.takeIf { it < itemCount }?.let {
            isSection(nextPosition)
        } ?: false
        return isAfterField and isBeforeSection
    }

    private fun shouldDisplayAllRoundedCorners(holderPosition: Int): Boolean {
        val prevPosition = holderPosition - 1
        val nextPosition = holderPosition + 1
        val isOnlyItem = itemCount == 1
        val isLastItem = holderPosition == itemCount - 1
        val isAfterSection = prevPosition.takeIf { it >= 0 }?.let {
            isSection(prevPosition)
        } ?: false
        val isBeforeSection = nextPosition.takeIf { it < itemCount }?.let {
            isSection(nextPosition)
        } ?: isLastItem
        return isOnlyItem or (isAfterSection && isBeforeSection)
    }

    fun updateSectionData(position: Int, isHeader: Boolean) {
        (getItem(position) as SectionUiModelImpl?)!!.setShowNextButton(
            !isHeader && position > 0 && getItem(
                position - 1
            ) !is SectionUiModelImpl
        )
        (getItem(position) as SectionUiModelImpl?)!!.setSectionNumber(getSectionNumber(position))
        (getItem(position) as SectionUiModelImpl?)!!.setLastSectionHeight(
            position > 0 && position == itemCount - 1 && getItem(
                position - 1
            ) !is SectionUiModelImpl
        )
    }

    private fun getSectionNumber(sectionPosition: Int): Int {
        var sectionNumber = 1
        for (i in 0 until sectionPosition) {
            if (getItem(i) is SectionUiModelImpl) {
                sectionNumber++
            }
        }
        return sectionNumber
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position)!!.layoutId
    }

    fun swap(updates: List<FieldUiModel>, commitCallback: Runnable) {
        sectionPositions = LinkedHashMap()
        for (fieldViewModel in updates) {
            if (fieldViewModel is SectionUiModelImpl) {
                sectionPositions[fieldViewModel.uid] = updates.indexOf(fieldViewModel)
            }
        }

        submitList(updates) {
            commitCallback.run()
        }
    }

    fun getSectionSize(): Int {
        return sectionPositions.size
    }

    fun getSectionForPosition(visiblePos: Int): SectionUiModelImpl? {
        val sectionPosition = sectionHandler.getSectionPositionFromVisiblePosition(
            visiblePos,
            isSection(visiblePos),
            ArrayList(sectionPositions.values)
        )
        val model = if (sectionPosition != -1) {
            getItem(sectionPosition)
        } else {
            null
        }

        return if (model is SectionUiModelImpl) {
            model
        } else {
            null
        }
    }

    fun getSectionPosition(sectionUid: String): Int? {
        return sectionPositions[sectionUid]
    }

    fun isSection(position: Int): Boolean {
        return if (position < itemCount) {
            getItemViewType(position) == R.layout.form_section
        } else {
            false
        }
    }

    override fun intent(intent: FormIntent) {
        onIntent?.let {
            it(intent)
        }
    }

    override fun recyclerViewEvent(uiEvent: RecyclerViewUiEvents) {
        onRecyclerViewUiEvents?.let {
            it(uiEvent)
        }
    }
}
