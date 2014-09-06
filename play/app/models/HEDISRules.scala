/*
 * Copyright (c) 2014 Dufresne Management Consulting LLC.
 */
package models

import org.joda.time.LocalDate

/**
 * Trait to define an HEDIS rule.
 *
 * Implementation of this trait are able to generate claims for a patient meeting the rule criteria.
 * The generated claims will be compliant to the rule randomly based on targetCompliance rate (percentage)
 */
trait HEDISRule {

  /**
   * Indicate the name of the rule for configuration and reporting purpose
   */
  def name: String

  /**
   * Indicate the full name of the rule (human readable)
   */
  def fullName: String

  /**
   * Indicate the rule description (human readable)
   */
  def description: String

  /**
   * Indicates the rate at which the patients are eligible to the  measure.
   *
   * To be eligible, the patient must first meet the demographic requirements.
   * Example, an \c eligibleRate of 25 for CDC H1C, means that 25% of patient of age between 18 - 75
   * will have diabetes. Note that all CDC measure should have the same \c eligibleRate
   */
  def eligibleRate: Int

  /**
   * Indicates the rate at which the patients meet the measure, in %
   *
   * (patient in numerator, i.e., meet measure) / (patient in denominator, i.e., not excluded from measure) * 100
   *
   * This rate does not apply to exclusions (patients excluded from measure).
   *
   */
  def meetMeasureRate: Int

  /**
   * Indicates the rate at which patients are excluded from measure, in %
   *
   * Fraction of eligible patients that meet the exclusion criteria:
   * (excluded patients) / (eligible patients)
   */
  def exclusionRate: Int

  /**
   * Generate the claims for the patient to be in the denominator and possibly in the numerator as well.
   *
   * The patient is randomly in the numerator based on the \c targetCompliance rate.
   */
  def generateClaims(persistenceLayer: PersistenceLayer, patient: Patient, provider: Provider): List[Claim]

  /**
   * Verify if the measure is applicable to the patient based on patient's
   * demographics only.
   *
   * The patient may still not be eligible to the measure if the clinical criteria are not met.
   */
  def isPatientMeetDemographic(patient: Patient): Boolean

  /**
   * Verify if patient is eligible to the measure
   *
   * Patient may be eligible to the measure but excluded if meet the exclusion criteria.
   */
  def isPatientEligible(patient: Patient, patientHistory: PatientHistory): Boolean

  /**
   * Verify if patient meet the exclusion condition of the measure
   *
   * Does not verify if patient is eligible, but simply the exclusion criteria
   */
  def isPatientExcluded(patient: Patient, patientHistory: PatientHistory): Boolean

  /**
   * Verify if the patient is in the numerator of the rule, i.e., meets the measure.
   */
  def isPatientMeetMeasure(patient: Patient, patientHistory: PatientHistory): Boolean

  /**
   * Verify if the patient is in the denominator of the rule, i.e., eligible to the measure and not excluded.
   */
  def isPatientInDenominator(patient: Patient, patientHistory: PatientHistory): Boolean

}

abstract class HEDISRuleBase(config: RuleConfig, hedisDate: LocalDate) extends HEDISRule {

  def eligibleRate: Int = config.eligibleRate
  def meetMeasureRate: Int = config.meetMeasureRate
  def exclusionRate: Int = config.exclusionRate

  def generateClaims(persistenceLayer: PersistenceLayer, patient: Patient, provider: Provider): List[Claim] = List.empty
  def isPatientMeetDemographic(patient: Patient): Boolean = true
  def isPatientEligible(patient: Patient, patientHistory: PatientHistory): Boolean = isPatientMeetDemographic(patient)
  def isPatientExcluded(patient: Patient, patientHistory: PatientHistory): Boolean = false
  def isPatientMeetMeasure(patient: Patient, patientHistory: PatientHistory): Boolean = true
  def isPatientInDenominator(patient: Patient, patientHistory: PatientHistory): Boolean = isPatientEligible(patient, patientHistory) && !isPatientExcluded(patient, patientHistory)
}

object HEDISRules {

  val createRuleByName: Map[String, (RuleConfig, LocalDate) => HEDISRule] = Map(
    "TEST" -> { (c, d) => new TestRule(c, d) },
    "BCS" -> { (c, d) => new BCSRule(c, d) })

}

// define all rules

/**
 * Breast Cancer Screening Rule
 */
class TestRule(config: RuleConfig, hedisDate: LocalDate) extends HEDISRuleBase(config, hedisDate) {

  def name = "TEST"
  def fullName = "Test Rule"
  def description = "This rule is for testing."

  override def generateClaims(persistenceLayer: PersistenceLayer, patient: Patient, provider: Provider): List[Claim] = {
    List(
      persistenceLayer.createClaim(patient.uuid, provider.uuid, new LocalDate(2014, 9, 5), "icd 1", Set("icd 1", "icd 2"), Set("icd p1"), "hcfaPOS", "ubRevenue", "cpt", "hcpcs"))
  }
}

/**
 * Breast Cancer Screening Rule
 */
class BCSRule(config: RuleConfig, hedisDate: LocalDate) extends HEDISRuleBase(config, hedisDate) {

  def name = "BCS"
  def fullName = "Breast Cancer Screening"
  def description = "The percentage of women between 50 - 74 years of age who had a mammogram to screen for breast cancer any time on or between October 1 two years prior to the measurement year and December 31 of the measurement year (27 months)."

  override def isPatientMeetDemographic(patient: Patient): Boolean = {
    val age = patient.age(hedisDate)
    patient.gender == "F" && age > 49 && age < 75
  }
}
