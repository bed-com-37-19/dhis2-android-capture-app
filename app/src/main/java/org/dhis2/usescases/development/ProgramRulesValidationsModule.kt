package org.dhis2.usescases.development

import android.content.Context
import dagger.Module
import dagger.Provides
import org.dhis2.Bindings.valueTypeHintMap
import org.dhis2.commons.di.dagger.PerActivity
import org.dhis2.data.forms.dataentry.FormUiModelColorFactoryImpl
import org.dhis2.data.forms.dataentry.FormView
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactory
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactoryImpl
import org.dhis2.data.forms.dataentry.fields.LayoutProviderImpl
import org.dhis2.form.data.FormRepositoryImpl
import org.dhis2.form.model.coroutine.FormDispatcher
import org.dhis2.form.ui.provider.DisplayNameProviderImpl
import org.dhis2.form.ui.provider.HintProviderImpl
import org.dhis2.form.ui.style.FormUiColorFactory
import org.dhis2.form.ui.validation.FieldErrorMessageProvider
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Manager

@Module
class ProgramRulesValidationsModule(val context: ProgramRulesValidationActivity) {

    @Provides
    @PerActivity
    fun ruleValidationModelFactory(ruleValidations: ProgramRulesValidations) =
        RulesValidationsModelFactory(
            ruleValidations
        )

    @Provides
    @PerActivity
    fun programRulesValidations(
        d2: D2,
        fieldViewModelFactory: FieldViewModelFactory
    ) = ProgramRulesValidations(d2, fieldViewModelFactory)

    @Provides
    @PerActivity
    fun fieldViewModelFactory(
        context: Context,
        colorFactory: FormUiColorFactory,
        d2: D2
    ): FieldViewModelFactory {
        return FieldViewModelFactoryImpl(
            context.valueTypeHintMap(),
            true,
            colorFactory,
            LayoutProviderImpl(),
            HintProviderImpl(context),
            DisplayNameProviderImpl(d2)
        )
    }

    @Provides
    @PerActivity
    fun provideFormUiColorFactory(): FormUiColorFactory {
        return FormUiModelColorFactoryImpl(context, false)
    }

    @Provides
    @PerActivity
    fun formView() = FormView.Builder()
        .repository(
            FormRepositoryImpl(
                null,
                FieldErrorMessageProvider(context),
                DisplayNameProviderImpl(D2Manager.getD2())
            )
        )
        .dispatcher(FormDispatcher())
        .onItemChangeListener { action ->
            context.rulesValidationsModel.handleAction(action)
        }
        .needToForceUpdate(true)
        .factory(context.supportFragmentManager)
        .build()
}
