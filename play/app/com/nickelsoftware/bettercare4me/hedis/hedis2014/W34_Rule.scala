/*
 * Copyright (c) 2014 Dufresne Management Consulting LLC.
 */
package com.nickelsoftware.bettercare4me.hedis.hedis2014

import scala.util.Random

import org.joda.time.DateTime
import org.joda.time.Interval

import com.nickelsoftware.bettercare4me.hedis.HEDISRule
import com.nickelsoftware.bettercare4me.hedis.HEDISRuleBase
import com.nickelsoftware.bettercare4me.hedis.Scorecard
import com.nickelsoftware.bettercare4me.models.Claim
import com.nickelsoftware.bettercare4me.models.MedClaim
import com.nickelsoftware.bettercare4me.models.Patient
import com.nickelsoftware.bettercare4me.models.PatientHistory
import com.nickelsoftware.bettercare4me.models.PersistenceLayer
import com.nickelsoftware.bettercare4me.models.Provider
import com.nickelsoftware.bettercare4me.models.RuleConfig
import com.nickelsoftware.bettercare4me.utils.Utils

object W34 {

  val name = "W34-HEDIS-2014"

  val wellChildVisit = "Well Child Visit"

  // CPT for well child visit
  val cptA = List("99382", "99383", "99392", "99393")
  val cptAS = cptA.toSet

  // ICD D for well child visit
  val icdDA = List("V20.2", "V70.0", "V70.3", "V70.5", "V70.6", "V70.8", "V70.9")
  val icdDAS = icdDA.toSet
}
/**
 * Well-Child Visits in the Third, Fourth, Fifth and Sixth Years of Life
 *
 * The percentage of members 3–6 years of age who received one or more well-child visits with a PCP during
 * the measurement year.
 *
 * NUMERATOR:
 * At least one well-child visit with a PCP during the measurement year. The PCP does
 * not have to be the practitioner assigned to the child.
 *
 */
class W34_Rule(config: RuleConfig, hedisDate: DateTime) extends HEDISRuleBase(config, hedisDate) {

  val name = W34.name
  val fullName = "Well-Child Visits in the Third, Fourth, Fifth and Sixth Years of Life"
  val description = "The percentage of members 3–6 years of age who received one or more well-child visits with a PCP during " +
    "the measurement year."

  override def isPatientMeetDemographic(patient: Patient): Boolean = {
    val age = patient.age(hedisDate)
    age >= 3 && age <= 6
  }

  import W34._

  // This rule has 100% eligibility when the demographics are meet
  override val eligibleRate: Int = 100

  // This rule has 0% exclusion when the demographics are meet
  override val exclusionRate: Int = 0

  override def scorePatientExcluded(scorecard: Scorecard, patient: Patient, ph: PatientHistory): Scorecard = scorecard.addScore(name, fullName, HEDISRule.excluded, false)

  override def generateMeetMeasureClaims(pl: PersistenceLayer, patient: Patient, provider: Provider): List[Claim] = {

    val days = Utils.daysBetween(hedisDate.minusYears(1), hedisDate)
    val dos = hedisDate.minusDays(Random.nextInt(days))

    pickOne(List(

      // One possible set of claims based on cpt
      () => List(pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos, dos, cpt = pickOne(cptA))),

      // Another possible set of claims based on ICD D
      () => List(pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos, dos, icdDPri = pickOne(icdDA)))))()
  }

  override def scorePatientMeetMeasure(scorecard: Scorecard, patient: Patient, ph: PatientHistory): Scorecard = {

    val measurementInterval = getIntervalFromYears(1)

    def rules = List[(Scorecard) => Scorecard](

      (s: Scorecard) => {
        val claims1 = filterClaims(ph.cpt, cptAS, { claim: MedClaim => measurementInterval.contains(claim.dos) })
        val claims2 = filterClaims(ph.icdD, icdDAS, { claim: MedClaim => measurementInterval.contains(claim.dos) })
        val claims = List.concat(claims1, claims2)
        s.addScore(name, fullName, HEDISRule.meetMeasure, wellChildVisit, claims)
      })

    applyRules(scorecard, rules)
  }

}
