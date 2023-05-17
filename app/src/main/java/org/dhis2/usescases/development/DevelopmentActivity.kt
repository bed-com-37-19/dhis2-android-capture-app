package org.dhis2.usescases.development

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.databinding.DataBindingUtil
import com.airbnb.android.showkase.models.Showkase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.dhis2.R
import org.dhis2.commons.featureconfig.ui.FeatureConfigView
import org.dhis2.databinding.DevelopmentActivityBinding
import org.dhis2.getBrowserIntent
import org.dhis2.ui.dialogs.signature.SignatureDialog
import org.dhis2.usescases.general.ActivityGlobalAbstract
import org.hisp.dhis.android.core.D2Manager.getD2
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringWriter
import java.io.Writer

class DevelopmentActivity : ActivityGlobalAbstract() {
    private var count = 0
    private var iconNames: List<String>? = null
    private var binding: DevelopmentActivityBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.development_activity)
        loadIconsDevTools()
        loadCrashControl()
        loadFeatureConfig()
        loadSignature()
        loadConflicts()
        loadShowkase()
    }

    private fun loadShowkase() {
        binding!!.showkaseButton.setOnClickListener {
            startActivity(Showkase.getBrowserIntent(context))
        }
    }

    private fun loadConflicts() {
        binding!!.addConflicts.setOnClickListener {
            val d2 = getD2()
            ConflictGenerator(d2).generate()
        }
        binding!!.clearConflicts.setOnClickListener {
            val d2 = getD2()
            ConflictGenerator(d2).clear()
        }
    }

    private fun loadIconsDevTools() {
        val `is` = resources.openRawResource(R.raw.icon_names)
        val writer: Writer = StringWriter()
        val buffer = CharArray(1024)
        try {
            val reader: Reader = BufferedReader(InputStreamReader(`is`, "UTF-8"))
            var n: Int
            while (reader.read(buffer).also { n = it } != -1) {
                writer.write(buffer, 0, n)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                `is`.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        val json = writer.toString()
        iconNames = Gson().fromJson(json, object : TypeToken<List<String?>?>() {}.type)
        count = 0
        binding!!.iconButton.setOnClickListener { nextDrawable() }
        binding!!.automaticErrorCheck.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> if (isChecked) nextDrawable() }
        renderIconForPosition(count)
    }

    private fun renderIconForPosition(position: Int) {
        val iconName = iconNames!![position]
        binding!!.iconInput.setText(iconName)
        val iconResource_negative =
            resources.getIdentifier(iconName + "_negative", "drawable", packageName)
        val iconResource_outline =
            resources.getIdentifier(iconName + "_outline", "drawable", packageName)
        val iconResource_positive =
            resources.getIdentifier(iconName + "_positive", "drawable", packageName)
        binding!!.iconInput.error = null
        binding!!.iconImagePossitive.setImageDrawable(null)
        binding!!.iconImageOutline.setImageDrawable(null)
        binding!!.iconImageNegative.setImageDrawable(null)
        binding!!.iconImagePossitiveTint.setImageDrawable(null)
        binding!!.iconImageOutlineTint.setImageDrawable(null)
        binding!!.iconImageNegativeTint.setImageDrawable(null)
        var hasError = false
        try {
            binding!!.iconImagePossitive.setImageResource(iconResource_positive)
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
        }
        try {
            binding!!.iconImageOutline.setImageResource(iconResource_outline)
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
        }
        try {
            binding!!.iconImageNegative.setImageResource(iconResource_negative)
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
        }
        try {
            binding!!.iconImagePossitiveTint.setImageResource(iconResource_positive)
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
        }
        try {
            binding!!.iconImageOutlineTint.setImageResource(iconResource_outline)
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
        }
        try {
            binding!!.iconImageNegativeTint.setImageResource(iconResource_negative)
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
        }
        if (hasError) {
            binding!!.iconInput.error = "This drawable has errors"
        } else if (binding!!.automaticErrorCheck.isChecked) {
            nextDrawable()
        }
    }

    private fun nextDrawable() {
        count++
        if (count == iconNames!!.size) {
            count = 0
            binding!!.automaticErrorCheck.isChecked = false
            return
        }
        renderIconForPosition(count)
    }

    private fun loadCrashControl() {
        binding!!.crashButton.setOnClickListener { view: View? -> throw IllegalArgumentException("KA BOOOOOM!") }
    }

    private fun loadFeatureConfig() {
        binding!!.featureConfigButton.setOnClickListener { view: View? ->
            startActivity(
                FeatureConfigView::class.java, null, false, false, null
            )
        }
    }

    private fun loadSignature() {
        binding!!.signature.setOnClickListener { view: View? ->
            SignatureDialog("Signature") { bitmap: Bitmap? -> Unit }.show(
                supportFragmentManager, "Signature"
            )
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_OK)
        finish()
    }
}