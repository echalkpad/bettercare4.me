/*
 * Copyright (c) 2014 Dufresne Management Consulting LLC.
 */
package com.nickelsoftware.bettercare4me.hedis.hedis2014;

import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import com.nickelsoftware.bettercare4me.hedis.HEDISRulesTestSpec
import com.nickelsoftware.bettercare4me.hedis.Scorecard
import org.joda.time.LocalDate

class CWP_RuleTestSpec extends PlaySpec {

  "The CWP_Rule class representing Pharyngitis Treatment for Children HEDIS rule" must {

    "validate patient that meet the measure criteria" in {

      for (i <- 1 to 25) {
        val (patient, patientHistory, rule) = HEDISRulesTestSpec.setupTest(CWP.name, "M", new LocalDate(2001, 6, 1).toDateTimeAtStartOfDay(), 100, 0, 100)
        val scorecard = rule.scoreRule(Scorecard(), patient, patientHistory)

        rule.isPatientEligible(scorecard) mustBe true
        rule.isPatientExcluded(scorecard) mustBe false
        rule.isPatientMeetMeasure(scorecard) mustBe true
      }
    }

    "validate patient that is excluded from the criteria" in {

      for (i <- 1 to 25) {
        val (patient, patientHistory, rule) = HEDISRulesTestSpec.setupTest(CWP.name, "M", new LocalDate(2001, 6, 1).toDateTimeAtStartOfDay(), 100, 100, 0)
        val scorecard = rule.scoreRule(Scorecard(), patient, patientHistory)

        rule.isPatientEligible(scorecard) mustBe true
        rule.isPatientExcluded(scorecard) mustBe true
        rule.isPatientMeetMeasure(scorecard) mustBe false
      }
    }

    "validate patient that does not meet the measure criteria and is not excluded" in {

      for (i <- 1 to 25) {
        val (patient, patientHistory, rule) = HEDISRulesTestSpec.setupTest(CWP.name, "F", new LocalDate(2001, 6, 1).toDateTimeAtStartOfDay(), 100, 0, 0)
        val scorecard = rule.scoreRule(Scorecard(), patient, patientHistory)

        rule.isPatientEligible(scorecard) mustBe true
        rule.isPatientExcluded(scorecard) mustBe false
        rule.isPatientMeetMeasure(scorecard) mustBe false
      }
    }
  }
}