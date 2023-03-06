package org.dhis2.form.ui.binding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import org.dhis2.form.databinding.FormSectionBinding
import org.dhis2.form.model.SectionUiModelImpl

@BindingAdapter("animateArrow")
fun ImageView.animateArrow(isSelected: Boolean) {
    val binding = FormSectionBinding.inflate(LayoutInflater.from(context))
    if (isSelected) {
        binding.openIndicator.scaleY = 1f
    }
    binding.openIndicator.animate()
        .scaleY(if (isSelected) 1f else -1f)
        .setDuration(200)
        .start()
}

@BindingAdapter("isEllipsisNeeded", "sectionName")
fun ImageView.isEllipsisNeeded(item: SectionUiModelImpl, sectionName: TextView) {
    val imageView = this
    val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            val layout = sectionName.takeIf { it.text == item.label }?.layout
            layout?.let {
                val isEllipsized = it.getEllipsisCount(0) > 0
                if (item.hasToShowDescriptionIcon(isEllipsized)) {
                    imageView.visibility = View.VISIBLE
                } else {
                    imageView.visibility = View.GONE
                }
                sectionName.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        }
    }
    sectionName.viewTreeObserver.addOnGlobalLayoutListener(listener)
}
