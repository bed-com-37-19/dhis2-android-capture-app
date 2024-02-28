package org.dhis2.form.data

import android.text.TextUtils
import io.reactivex.Flowable
import io.reactivex.Single
import org.dhis2.bindings.blockingGetValueCheck
import org.dhis2.bindings.userFriendlyValue
import org.dhis2.commons.resources.MetadataIconProvider
import org.dhis2.commons.date.DateUtils
import org.dhis2.commons.resources.ResourceManager
import org.dhis2.form.R
import org.dhis2.form.data.metadata.FormBaseConfiguration
import org.dhis2.form.model.FieldUiModel
import org.dhis2.form.model.OptionSetConfiguration
import org.dhis2.form.ui.FieldViewModelFactory
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.common.FeatureType
import org.hisp.dhis.android.core.common.ObjectStyle
import org.hisp.dhis.android.core.common.ValueType
import org.hisp.dhis.android.core.dataelement.DataElement
import org.hisp.dhis.android.core.imports.ImportStatus
import org.hisp.dhis.android.core.program.ProgramStageDataElement
import org.hisp.dhis.android.core.program.ProgramStageSection
import org.hisp.dhis.android.core.program.SectionRenderingType
import java.util.Date

class EventRepository(
    private val fieldFactory: FieldViewModelFactory,
    private val eventUid: String,
    private val d2: D2,
    private val metadataIconProvider: MetadataIconProvider,
    private val resources: ResourceManager,
) : DataEntryBaseRepository(FormBaseConfiguration(d2), fieldFactory) {

    private val event by lazy {
        d2.eventModule().events().uid(eventUid)
            .blockingGet()
    }

    override val programUid by lazy {
        event?.program()
    }

    private val sectionMap by lazy {
        d2.programModule().programStageSections()
            .byProgramStageUid().eq(event?.programStage())
            .withDataElements()
            .blockingGet()
            .map { section -> section.uid() to section }
            .toMap()
    }

    private val programStage by lazy {
        d2.programModule()
            .programStages()
            .uid(event?.programStage())
            .blockingGet()
    }

    override fun sectionUids(): Flowable<List<String>> {
        val sectionUIDs = mutableListOf(EVENT_DETAILS_SECTION_UID)
        if (sectionMap.keys.isEmpty()) {
            sectionUIDs.add(EVENT_DATA_SECTION_UID)
        } else {
            sectionUIDs.addAll(sectionMap.keys.toList())
        }
        return Flowable.just(sectionUIDs)
    }

    override fun list(): Flowable<List<FieldUiModel>> {
        return d2.programModule().programStageSections()
            .byProgramStageUid().eq(event?.programStage())
            .withDataElements()
            .get()
            .flatMap { programStageSection ->
                if (programStageSection.isEmpty()) {
                    getFieldsForSingleSection()
                        .map { singleSectionList ->
                            val list = getEventDataSectionList()
                            list.addAll(singleSectionList)
                            list
                        }
                } else {
                    getFieldsForMultipleSections()
                }
            }.map { list ->
                val fields = getEventDetails()
                fields.addAll(list)
                fields.add(fieldFactory.createClosingSection())
                fields.toList()
            }.toFlowable()
    }

    private fun getEventDataSectionList(): MutableList<FieldUiModel> {
        return mutableListOf(
            fieldFactory.createSection(
                sectionUid = EVENT_DATA_SECTION_UID,
                sectionName = resources.formatWithEventLabel(
                    stringResource = R.string.event_data_section_title,
                    programStageUid = event?.programStage(),
                ),
                description = null,
                isOpen = true,
                totalFields = 0,
                completedFields = 0,
                rendering = SectionRenderingType.LISTING.name,
            ),
        )
    }

    override fun isEvent(): Boolean {
        return true
    }

    override fun getSpecificDataEntryItems(uid: String): List<FieldUiModel> {
        // pending implementation in Event Form
        return emptyList()
    }

    private fun getEventDetails(): MutableList<FieldUiModel> {
        val eventDataItems = mutableListOf<FieldUiModel>()
        eventDataItems.apply {
            add(createEventDetailsSection())
            add(createEventReportDateField())
            add(createEventOrgUnitField())
        }
        return eventDataItems
    }

    private fun createEventOrgUnitField(): FieldUiModel {
        return fieldFactory.create(
            id = EVENT_ORG_UNIT_UID,
            label = resources.getString(R.string.org_unit),
            valueType = ValueType.ORGANISATION_UNIT,
            mandatory = true,
            optionSet = null,
            value = getStoredOrgUnit(),
            programStageSection = EVENT_DETAILS_SECTION_UID,
            editable = false,
            description = null,
        )
    }

    private fun getStoredOrgUnit(): String? {
        return event?.organisationUnit()?.let { orgUnitUID ->
            d2.organisationUnitModule().organisationUnits()
                .byUid()
                .eq(orgUnitUID)
                .one().blockingGet()
        }?.displayName()
    }

    private fun createEventReportDateField(): FieldUiModel {
        val dateValue = getEventReportDate()?.let { date ->
            DateUtils.oldUiDateFormat().format(date)
        }

        return fieldFactory.create(
            id = EVENT_REPORT_DATE_UID,
            label = programStage?.executionDateLabel() ?: resources.formatWithEventLabel(
                R.string.event_label_date,
                programStage?.uid(),
            ),
            valueType = ValueType.DATE,
            mandatory = true,
            optionSet = null,
            value = dateValue,
            programStageSection = EVENT_DETAILS_SECTION_UID,
            allowFutureDates = false,
            editable = true,
            description = null,
        )
    }

    private fun getEventReportDate() = when {
        event != null -> event?.eventDate()
        programStage?.periodType() != null -> getDateBasedOnPeriodType()
        else -> DateUtils.getInstance().today
    }

    private fun getDateBasedOnPeriodType(): Date {
        return DateUtils.getInstance()
            .getNextPeriod(
                programStage?.periodType(),
                DateUtils.getInstance().today,
                0,
            )
    }

    private fun createEventDetailsSection(): FieldUiModel {
        return fieldFactory.createSection(
            sectionUid = EVENT_DETAILS_SECTION_UID,
            sectionName = resources.formatWithEventLabel(
                stringResource = R.string.event_details_section_title,
                programStageUid = event?.programStage(),
            ),
            description = programStage?.description(),
            isOpen = false,
            totalFields = 0,
            completedFields = 0,
            rendering = SectionRenderingType.LISTING.name,
        )
    }

    private fun getFieldsForSingleSection(): Single<List<FieldUiModel>> {
        return Single.fromCallable {
            val stageDataElements = d2.programModule().programStageDataElements().withRenderType()
                .byProgramStage().eq(event?.programStage())
                .orderBySortOrder(RepositoryScope.OrderByDirection.ASC)
                .blockingGet()

            stageDataElements.map { programStageDataElement ->
                transform(programStageDataElement, EVENT_DATA_SECTION_UID)
            }
        }
    }

    private fun getFieldsForMultipleSections(): Single<List<FieldUiModel>> {
        return Single.fromCallable {
            val fields = mutableListOf<FieldUiModel>()
            sectionMap.values.forEach { programStageSection ->
                fields.add(
                    transformSection(
                        programStageSection.uid(),
                        programStageSection.displayName(),
                        programStageSection.displayDescription(),
                    ),
                )
                programStageSection.dataElements()?.forEach { dataElement ->
                    d2.programModule().programStageDataElements().withRenderType()
                        .byProgramStage().eq(event?.programStage())
                        .byDataElement().eq(dataElement.uid())
                        .one().blockingGet()?.let {
                            fields.add(
                                transform(it, programStageSection.uid()),
                            )
                        }
                }
            }
            return@fromCallable fields
        }
    }

    private fun transform(
        programStageDataElement: ProgramStageDataElement,
        sectionUid: String,
    ): FieldUiModel {
        val de = d2.dataElementModule().dataElements().uid(
            programStageDataElement.dataElement()!!.uid(),
        ).blockingGet()
        val uid = de?.uid() ?: ""
        val displayName = de?.displayName() ?: ""
        val valueType = de?.valueType()
        val mandatory = programStageDataElement.compulsory() ?: false
        val optionSet = de?.optionSetUid()
        val valueRepository =
            d2.trackedEntityModule().trackedEntityDataValues().value(eventUid, uid)
        val programStageSection: ProgramStageSection? = sectionMap.values.firstOrNull { section ->
            section.dataElements()?.map { it.uid() }?.contains(de?.uid()) ?: false
        }
        var dataValue = when {
            valueRepository.blockingExists() -> valueRepository.blockingGet()?.value()
            else -> null
        }
        val friendlyValue = dataValue?.let {
            valueRepository.blockingGetValueCheck(d2, uid).userFriendlyValue(d2)
        }
        val allowFutureDates = programStageDataElement.allowFutureDate() ?: false
        val formName = de?.displayFormName()
        val description = de?.displayDescription()
        var optionSetConfig: OptionSetConfiguration? = null
        if (!TextUtils.isEmpty(optionSet)) {
            if (!TextUtils.isEmpty(dataValue) && d2.optionModule().options().byOptionSetUid()
                    .eq(optionSet).byCode()
                    .eq(dataValue)
                    .one().blockingExists()
            ) {
                dataValue =
                    d2.optionModule().options().byOptionSetUid().eq(optionSet)
                        .byCode()
                        .eq(dataValue).one().blockingGet()?.displayName()
            }
            val optionCount =
                d2.optionModule().options().byOptionSetUid().eq(optionSet)
                    .blockingCount()
            optionSetConfig = OptionSetConfiguration.config(optionCount) {
                val options = d2.optionModule().options().byOptionSetUid().eq(optionSet)
                    .orderBySortOrder(RepositoryScope.OrderByDirection.ASC).blockingGet()

                val metadataIconMap = options.associate { it.uid() to metadataIconProvider(it.style()) }

                OptionSetConfiguration.OptionConfigData(
                    options = options,
                    metadataIconMap = metadataIconMap,
                )
            }
        }
        val fieldRendering = getValueTypeDeviceRendering(programStageDataElement)
        val objectStyle = getObjectStyle(de)

        val (error, warning) = de?.uid()?.let { deUid ->
            getConflictErrorsAndWarnings(deUid, dataValue)
        } ?: Pair(null, null)

        val isOrgUnit =
            valueType === ValueType.ORGANISATION_UNIT
        val isDate = valueType != null && valueType.isDate
        if (!isOrgUnit && !isDate) {
            dataValue = friendlyValue
        }
        val renderingType = getSectionRenderingType(programStageSection)
        val featureType = getFeatureType(valueType)

        var fieldViewModel = fieldFactory.create(
            uid,
            formName ?: displayName,
            valueType!!,
            mandatory,
            optionSet,
            dataValue,
            sectionUid,
            allowFutureDates,
            isEventEditable(),
            renderingType,
            description,
            fieldRendering,
            objectStyle,
            de.fieldMask(),
            optionSetConfig,
            featureType,
        )

        if (!error.isNullOrEmpty()) {
            fieldViewModel = fieldViewModel.setError(error)
        }

        if (!warning.isNullOrEmpty()) {
            fieldViewModel = fieldViewModel.setWarning(warning)
        }

        return fieldViewModel
    }

    private fun getConflictErrorsAndWarnings(
        dataElementUid: String,
        dataValue: String?,
    ): Pair<String?, String?> {
        var error: String? = null
        var warning: String? = null

        val conflicts = d2.importModule().trackerImportConflicts()
            .byEventUid().eq(eventUid)
            .blockingGet()

        val conflict = conflicts
            .find { it.dataElement() == dataElementUid }

        when (conflict?.status()) {
            ImportStatus.WARNING -> warning = getError(conflict, dataValue)
            ImportStatus.ERROR -> error = getError(conflict, dataValue)
            else -> {
                // no-op
            }
        }

        return Pair(error, warning)
    }

    private fun getObjectStyle(de: DataElement?) = de?.style() ?: ObjectStyle.builder().build()

    private fun getValueTypeDeviceRendering(programStageDataElement: ProgramStageDataElement) =
        if (programStageDataElement.renderType() != null) {
            programStageDataElement.renderType()!!
                .mobile()
        } else {
            null
        }

    private fun getFeatureType(valueType: ValueType?) = when (valueType) {
        ValueType.COORDINATE -> FeatureType.POINT
        else -> null
    }

    private fun getSectionRenderingType(programStageSection: ProgramStageSection?) =
        programStageSection?.renderType()?.mobile()?.type()

    private fun isEventEditable() = d2.eventModule().eventService().blockingIsEditable(eventUid)

    companion object {
        const val EVENT_DETAILS_SECTION_UID = "EVENT_DETAILS_SECTION_UID"
        const val EVENT_REPORT_DATE_UID = "EVENT_REPORT_DATE_UID"
        const val EVENT_ORG_UNIT_UID = "EVENT_ORG_UNIT_UID"
        const val EVENT_DATA_SECTION_UID = "EVENT_DATA_SECTION_UID"
    }
}
