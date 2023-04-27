package org.dhis2.form.data

import org.hisp.dhis.rules.models.RuleEffect

interface RuleEngineRepository {
    fun calculate(cachedValues:Map<String,String?> = emptyMap()): List<RuleEffect>
}
