package com.apex.core.settings

import java.io.File
import java.net.InetSocketAddress

import com.apex.common.ApexLogging
import com.apex.crypto.Ecdsa.{PrivateKey, PublicKey}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

case class NetworkSettings(nodeName: String,
                           addedMaxDelay: Option[FiniteDuration],
                           localOnly: Boolean,
                           knownPeers: Seq[InetSocketAddress],
                           bindAddress: InetSocketAddress,
                           maxConnections: Int,
                           connectionTimeout: FiniteDuration,
                           upnpEnabled: Boolean,
                           upnpGatewayTimeout: Option[FiniteDuration],
                           upnpDiscoverTimeout: Option[FiniteDuration],
                           declaredAddress: Option[InetSocketAddress],
                           handshakeTimeout: FiniteDuration,
                           appVersion: String,
                           agentName: String,
                           maxPacketSize: Int,
                           controllerTimeout: Option[FiniteDuration])

case class NetworkTimeProviderSettings(server: String, updateEvery: FiniteDuration, timeout: FiniteDuration)
                                                      

case class ApexSettings(dataDir: File,
                          logDir: File,
                          network: NetworkSettings,
                          ntp: NetworkTimeProviderSettings,
                          genesisConfig: ConsesusConfig
                         )


case class ConsesusConfig(produceInterval: Int,
                          acceptableTimeError: Int,
                          initialWitness: Array[Witness])

case class Witness(name: String,
                   pubkey: PublicKey,
                   privkey: Option[PrivateKey])

object ApexSettings extends ApexLogging with SettingsReaders {

  protected val configPath: String = "apex"

  def readConfigFromPath(userConfigPath: Option[String], configPath: String): Config = {

    val maybeConfigFile: Option[File] = userConfigPath.map(filename => new File(filename)).filter(_.exists())
      .orElse(userConfigPath.flatMap(filename => Option(getClass.getClassLoader.getResource(filename))).
        map(r => new File(r.toURI)).filter(_.exists()))

    val config = maybeConfigFile match {
      case None =>
        log.warn("NO CONFIGURATION FILE WAS PROVIDED. STARTING WITH DEFAULT SETTINGS FOR TESTNET!")
        ConfigFactory.load()
      case Some(file) =>
        val cfg = ConfigFactory.parseFile(file)
        if (!cfg.hasPath(configPath)) {
          throw new Error("Malformed configuration file was provided! Aborting!")
        }
        ConfigFactory
          .defaultOverrides()
          .withFallback(cfg) // 
          .withFallback(ConfigFactory.defaultApplication())
          .withFallback(ConfigFactory.defaultReference()) // 加载"src/main/resources/reference.conf"
          .resolve()
    }

    config
  }

}