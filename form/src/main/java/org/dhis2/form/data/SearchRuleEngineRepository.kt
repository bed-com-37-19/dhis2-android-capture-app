package org.dhis2.form.data

import java.util.Date
import org.dhis2.Bindings.blockingGetCheck
import org.dhis2.form.bindings.toRuleAttributeValue
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.program.Program
import org.hisp.dhis.rules.RuleEngine
import org.hisp.dhis.rules.RuleEngineContext
import org.hisp.dhis.rules.models.RuleAttributeValue
import org.hisp.dhis.rules.models.RuleEffect
import org.hisp.dhis.rules.models.RuleEnrollment
import org.hisp.dhis.rules.models.RuleEvent
import org.hisp.dhis.rules.models.TriggerEnvironment

class SearchRuleEngineRepository(
    private val d2: D2,
    private val programUid: String
) : RuleEngineRepository {

    private val ruleRepository = RulesRepository(d2)
    private val ruleEngine: RuleEngine
    private var ruleEnrollmentBuilder: RuleEnrollment.Builder

    private val program: Program by lazy {
        d2.programModule().programs()
            .uid(programUid)
            .blockingGet()
    }

    init {

        val rules = ruleRepository.rulesNew(program.uid()).blockingGet()
        val variables = ruleRepository.ruleVariables(program.uid()).blockingGet()
        val supplData = ruleRepository.supplementaryData("").blockingGet()
        val constants = ruleRepository.queryConstants().blockingGet()
        val events = emptyList<RuleEvent>()

        ruleEngine = RuleEngineContext.builder()
            .rules(rules)
            .ruleVariables(variables)
            .supplementaryData(supplData)
            .constantsValue(constants)
            .build().toEngineBuilder().apply {
                triggerEnvironment(TriggerEnvironment.ANDROIDCLIENT)
                events(events)
            }.build()

        ruleEnrollmentBuilder = RuleEnrollment.builder()
            .enrollment("search")
            .incidentDate(Date())
            .enrollmentDate(Date())
            .status(RuleEnrollment.Status.ACTIVE)
            .organisationUnit("")
            .organisationUnitCode("")
            .programName("search")
    }

    override fun calculate(cachedValues:Map<String,String?>): List<RuleEffect> {
        val attributes = cachedValues.mapNotNull { (attribute, value) ->
            value?.let { RuleAttributeValue.create(attribute, value) }
        }
        return try {
            ruleEngine.evaluate(ruleEnrollmentBuilder.attributeValues(attributes).build()).call()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
