/*
 * Copyright (c) 2014 Dufresne Management Consulting LLC.
 */
package com.nickelsoftware.bettercare4me.actors

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import com.nickelsoftware.bettercare4me.hedis.HEDISScoreSummary
import com.nickelsoftware.bettercare4me.models.ClaimGeneratorConfig
import play.api.Logger
import com.nickelsoftware.bettercare4me.utils.Properties
import com.nickelsoftware.bettercare4me.utils.NickelException
import org.apache.spark.SparkException
import com.nickelsoftware.bettercare4me.utils.Utils

/**
 * Simple object to lead the spark configuration from file
 *
 * Loading from Properties.sparkConfig
 */
object SparkConfig {

  private val fname = Properties.sparkConfig.path
  lazy val config = Utils.loadYamlConfig(fname)

  def master = config.getOrElse("master", "local[3]").asInstanceOf[String]
  def appName = config.getOrElse("appName", "Local Bettercare4.me App").asInstanceOf[String]

  Logger.info("Spark Configuration leaded from " + fname)
  Logger.info("Spark master: " + master)
  Logger.info("Spark app name: " + appName)

}

/**
 * Helper object to distribute the workload of generating the patients, providers and claims
 * over a spark cluster.
 */
object ClaimGeneratorSparkHelper {

  /**
   * Generate all patients, providers, and claims based on config
   * @param config is the claim generator configuration
   * @returns triple with (nbr of patients, nbr providers, nbr claims) generated
   */
  def generateClaims(generator: ClaimGeneratorHelper, configTxt: String): ClaimGeneratorCounts = {

    val conf = new SparkConf()
      .setMaster(SparkConfig.master)
      .setAppName(SparkConfig.appName)

    val sc = new SparkContext(conf)
    Logger.info("generateClaims: Spark master: " + sc.master)
    Logger.info("generateClaims: Spark app name: " + sc.appName)

    val result = try {

      // broadcast the config so it is available to each node of the cluster
      val broadcastConfigTxt = sc.broadcast(configTxt)
      val broadcastGenerator = sc.broadcast(generator)

      val config = ClaimGeneratorConfig.loadConfig(configTxt)

      // create the nbrGen jobs to run, ensuring the rdd is sliced with one job per slice, ie nbrGen slices
      val rdd = sc.parallelize(1 to config.nbrGen, config.nbrGen) map { igen => broadcastGenerator.value.generateClaims(igen, broadcastConfigTxt.value) }

      // combine the result of each job to get the total count of patients, providers and claims
      val res = rdd reduce { (a, b) => a + b }

      Logger.info("The claim generator produced:")
      Logger.info(res.toString)
      res

    } catch {
      case se: SparkException =>
        Logger.error("ClaimGeneratorSparkHelper.generateClaims: SparkException caught 1! " + se.getMessage())
        throw NickelException("ClaimGeneratorSparkHelper.generateClaims: SparkException caught 1! " + se.getMessage())

      case ex: Exception =>
        Logger.error("ClaimGeneratorSparkHelper.generateClaims: Exception caught 2! " + ex.toString())
        throw NickelException("ClaimGeneratorSparkHelper.generateClaims: Exception caught 2! " + ex.getMessage())

    } finally {
      sc.stop
    }
    result
  }

  def processGeneratedClaims(generator: ClaimGeneratorHelper, configTxt: String): HEDISScoreSummary = {

    val conf = new SparkConf()
      .setMaster(SparkConfig.master)
      .setAppName(SparkConfig.appName)

    val sc = new SparkContext(conf)
    Logger.info("processGeneratedClaims: Spark master: " + sc.master)
    Logger.info("processGeneratedClaims: Spark app name: " + sc.appName)

    val result = try {

      // broadcast the config so it is available to each node of the cluster
      val broadcastConfigTxt = sc.broadcast(configTxt)
      val broadcastGenerator = sc.broadcast(generator)

      val config = ClaimGeneratorConfig.loadConfig(configTxt)

      // create the nbrGen jobs to run, ensuring the rdd is sliced with one job per slice, ie nbrGen slices
      val rdd = sc.parallelize(1 to config.nbrGen, config.nbrGen) map { igen => broadcastGenerator.value.processGeneratedClaims(igen, broadcastConfigTxt.value) }

      // combine the result of each job to get the total count of patients, providers and claims
      val res = rdd reduce { (a, b) => a + b }

      // persist the HEDIS Score Summary in Cassandra (Cassandra only)
      generator.saveHEDISScoreSummary(res, configTxt)

      // create the paginated list of patients from rule_scorecards table to rule_scorecards_paginated table - Cassandra only
      val rdd2 = sc.parallelize(config.rulesConfig map (_.name), config.rulesConfig.size) map { ruleName => (ruleName, broadcastGenerator.value.paginateRuleScorecards(ruleName, broadcastConfigTxt.value)) }

      Logger.info("The pagination of the rule scorecards produced:")
      rdd2.collect foreach { case (ruleName, pageCnt) => Logger.info(ruleName + " has " + pageCnt + " pages of 20 patients each.") }
      res

    } catch {
      case ex: Exception =>
        Logger.error("ClaimGeneratorSparkHelper.processGeneratedClaims: Exception caught 1! " + ex.toString())
        throw NickelException("ClaimGeneratorSparkHelper.processGeneratedClaims: Exception caught 1! " + ex.getMessage())

    } finally {
      sc.stop
    }
    result
  }
}