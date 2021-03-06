/*
 * Copyright (c) 2014 Dufresne Management Consulting LLC.
 */
package com.nickelsoftware.bettercare4me.models

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap

import org.joda.time.DateTime
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object ClaimGeneratorConfig {

  def loadConfig(text: String): ClaimGeneratorConfig = {
    val yaml = new Yaml(new SafeConstructor());
    val data  = yaml.load(text).asInstanceOf[java.util.Map[String, Object]]
    ClaimGeneratorConfig(mapAsScalaMap(data).toMap)
  }
}

/**
 * Class holding the parameters of the simulation
 *
 * @param runName Name for the HEDIS run, should be unique (used as primary key in Cassandra).
 * @param basePath base path for the output file. Simulator may add date to path and create new directory.
 * @param baseFname Base file name for the output files (claims, patients, and providers)
 * @param generator indicate which ClaimGeneratorHelper to use ('file', 'file-spark', 'cassandra')
 * @param nGen number of generation to simulate
 * @param nPatients number of patients to simulate per generation
 * @param nProviders number of providers to simulate per generation
 * @param hedisDate date to used for HEDIS report date for the simulation (usually Dec 31 of the measurement year)
 * @param populationHealthMeasures list of measures name in the population health category (for reporting categorization)
 * @param rulesConfig list of configuration parameter for each HEDISRule used in the simulation
 */
case class ClaimGeneratorConfig(config: Map[String, Object]) {
  def runName: String = config.getOrElse("runName", "HEDIS Simulator").asInstanceOf[String]
  def basePath: String = config.getOrElse("basePath", "./data/hedis-data").asInstanceOf[String]
  def baseFname: String = config.getOrElse("baseFname", "hedis").asInstanceOf[String]
  def generator: String = config.getOrElse("generator", "file").asInstanceOf[String]
  def nbrGen: Int = config.getOrElse("nbrGen", 1).asInstanceOf[Int]
  def nbrPatients: Int = config.getOrElse("nbrPatients", 1).asInstanceOf[Int]
  def nbrProviders: Int = config.getOrElse("nbrProviders", 1).asInstanceOf[Int]
  def hedisDatejd: java.util.Date = config.getOrElse("hedisDateTxt",  java.text.DateFormat.getInstance().parse("2014-12-31T00:00:00.00-05:00")).asInstanceOf[java.util.Date]
  def rulesConfig: List[RuleConfig] = {
    val list = config.getOrElse("rulesConfig", new java.util.ArrayList()).asInstanceOf[java.util.ArrayList[java.util.Map[String, Object]]]
    val l = list map { m => RuleConfig(mapAsScalaMap(m).toMap) }
    l.toList
  }
  
  private def selectedMeasures(name: String): List[String] = {
    val list = config.getOrElse(name, new java.util.ArrayList()).asInstanceOf[java.util.ArrayList[String]]
    list.toList
  }
  
  def populationHealthMeasures: List[String] = selectedMeasures("populationHealthMeasures")
  def wellChildVisits: List[String] = selectedMeasures("wellChildVisits")
  def comprehensiveDiabetesCare: List[String] = selectedMeasures("comprehensiveDiabetesCare")
  def additionalChronicCareMeasures: List[String] = selectedMeasures("additionalChronicCareMeasures")
  def otherMeasures: List[String] = selectedMeasures("otherMeasures")

  def hedisDate = {
    new DateTime(hedisDatejd)
  }
}

/**
 * Class holding configuration parameters for HEDISRule
 *
 * @param name of the rule this configuration applies to
 * @param eligibleRate the rate at which the patients are eligible to the  measure
 * @param meetMeasureRate the rate at which the patients meet the measure, in %
 * @param exclusionRate the rate at which patients are excluded from measure, in %
 * @param simulationParity is the name of the rule to have same simulation scores (isEligible, isExcluded, isMeetMeasure) to avoid conflicts
 */
case class RuleConfig(config: Map[String, Object]) {
  def name: String = config.getOrElse("name", "ruleName").asInstanceOf[String]
  def eligibleRate: Int = config.getOrElse("eligibleRate", 100).asInstanceOf[Int]
  def meetMeasureRate: Int = config.getOrElse("meetMeasureRate", 100).asInstanceOf[Int]
  def exclusionRate: Int = config.getOrElse("exclusionRate", 0).asInstanceOf[Int]
  def simulationParity: String = config.getOrElse("simulationParity", "").asInstanceOf[String]
  def otherParams: Map[String, String] = {
    val m = mapAsScalaMap(config.getOrElse("otherParams", new java.util.HashMap()).asInstanceOf[java.util.HashMap[String, String]])
    m.toMap
  }
  
  def simParityRuleName = if(simulationParity == "") name else simulationParity
}
