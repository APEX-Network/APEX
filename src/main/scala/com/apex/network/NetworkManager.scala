package com.apex.network

import java.net.InetSocketAddress

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import com.apex.common.ApexLogging
import com.apex.core.settings.NetworkSettings
import com.apex.network.upnp.UPnP
import com.apex.network.peer.PeerHandlerManager.ReceivableMessages.PeerHandler

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.io.IO
import akka.io.Tcp
import akka.io.Tcp.Bind
import akka.io.Tcp.Bound
import akka.io.Tcp.CommandFailed
import akka.io.Tcp.Connect
import akka.io.Tcp.Connected
import akka.io.Tcp.SO.KeepAlive
import akka.util.Timeout
import com.apex.network.message.Message
import com.apex.core.utils.NetworkTimeProvider


/**
  * 控制所有网络交互
  * 必须是单例
  */
class NetworkManager(settings: NetworkSettings,upnp: UPnP,peerHandlerManagerRef:ActorRef,timeProvider: NetworkTimeProvider)(implicit ec: ExecutionContext) extends Actor with ApexLogging {

  import NetworkManager.ReceivableMessages._
  import PeerConnectionManager.ReceivableMessages.CloseConnection
  import com.apex.network.peer.PeerHandlerManager.ReceivableMessages.Disconnected
  import PeerConnectionManager.ReceivableMessages.GetHandlerToPeerConnectionManager
  
  private implicit val system: ActorSystem = context.system

  private implicit val timeout: Timeout = Timeout(settings.controllerTimeout.getOrElse(5 seconds))

  private val tcpManager = IO(Tcp)

  lazy val localAddress = settings.bindAddress

  //发送给peers的地址
  lazy val externalSocketAddress: Option[InetSocketAddress] = None /*{
    settings.declaredAddress orElse {
      if (settings.upnpEnabled) {
        upnp.externalAddress.map(a => new InetSocketAddress(a, settings.bindAddress.getPort))
      } else None
    }
  }*/

  log.info(s"Declared address: $externalSocketAddress")


  lazy val connTimeout = Some(settings.connectionTimeout)

  //绑定来侦听传入的连接
  tcpManager ! Bind(self, localAddress, options = KeepAlive(true) :: Nil, pullMode = false)

  private def bindingLogic: Receive = {
    case Bound(_) =>
      log.info("成功绑定到端口 " + settings.bindAddress.getPort)
      val knownPeers = settings.knownPeers
      if(knownPeers.size>0){
        for(knownPeer <- knownPeers){
           self ! ConnectTo(new InetSocketAddress(knownPeer.getAddress, knownPeer.getPort))
        }
      }
    case CommandFailed(_: Bind) =>
      log.error("端口 " + settings.bindAddress.getPort + " already in use!")
      context stop self
  }


  private val outgoing = mutable.Set[InetSocketAddress]()
  var handler:ActorRef = system.actorOf(Props.empty)

  //首次启动连接远程节点
  def peerLogic: Receive = {
    case ConnectTo(remote) =>
      log.info(s"Connecting to: $remote")
      outgoing += remote
      tcpManager ! Connect(remote,
                          localAddress = externalSocketAddress,
                          options = KeepAlive(true) :: Nil,
                          timeout = connTimeout,
                          pullMode = true)
    case DisconnectFrom(peer) =>
      log.info(s"Disconnected from ${peer.socketAddress}")
      peer.handlerRef ! CloseConnection
      peerHandlerManagerRef ! Disconnected(peer.socketAddress)
      
//    case Blacklist(peer) =>
//      peer.handlerRef ! PeerConnectionmanager.ReceivableMessages.Blacklist
//      peerHandlerManagerRef ! Disconnected(peer.socketAddress)
      
    //远程连接绑定到本地端口,所有连接的节点同时互相绑定
    case Connected(remote, local) =>
      val direction: ConnectionType = if(outgoing.contains(remote)) Outgoing else Incoming
      val logMsg = direction match {
        case Incoming => s"传入的远程连接 $remote 绑定到本地 $local"
        case Outgoing => s"输入的远程连接 $remote 绑定到本地 $local"
      }
      log.info(logMsg)
      val connection = sender()
      val handlerProps: Props = PeerConnectionManagerRef.props(settings,peerHandlerManagerRef,connection, direction,externalSocketAddress,remote,self,timeProvider)
      handler = context.actorOf(handlerProps)
      outgoing -= remote
      
    case CommandFailed(c: Connect) =>
      outgoing -= c.remoteAddress
      log.info("未能连接到 : " + c.remoteAddress)
      peerHandlerManagerRef ! Disconnected(c.remoteAddress)
  }
  def getHandler: Receive = {
    case GetHandlerToPeerConnectionManager =>
      peerHandlerManagerRef ! PeerHandler(handler) //handler做为消息发送到peerHandlerManager，由peerHandlerManager统一管理
  }
//  def interfaceCalls: Receive = {
//    case ShutdownNetwork =>
//      log.info("关闭所有连接和解除绑定端口")
//      (peerManagerRef ? FilterPeers(Broadcast))
//        .map(_.asInstanceOf[Seq[ConnectedPeer]])
//        .foreach(_.foreach(_.handlerRef ! CloseConnection))
//      self ! Unbind
//      context stop self
//  }

  override def receive: Receive = bindingLogic orElse peerLogic orElse getHandler orElse{
    case CommandFailed(cmd: Tcp.Command) =>
      log.info("执行命令失败 : " + cmd)

    case nonsense: Any =>
      log.warn(s"NetworkController: 未知的错误  $nonsense")
  }
}

object NetworkManager {
  object ReceivableMessages {
    case class ConnectTo(address: InetSocketAddress)
    case class DisconnectFrom(peer: ConnectedPeer)
    case class Blacklist(peer: ConnectedPeer)
  }
}

object NetworkManagerRef {
  def props(settings: NetworkSettings,upnp: UPnP,peerHandlerManagerRef:ActorRef,timeProvider: NetworkTimeProvider)(implicit ec: ExecutionContext): Props =
    Props(new NetworkManager(settings, upnp,peerHandlerManagerRef:ActorRef,timeProvider))

  def apply(settings: NetworkSettings,upnp: UPnP,peerHandlerManagerRef:ActorRef,timeProvider: NetworkTimeProvider)(implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, upnp,peerHandlerManagerRef:ActorRef,timeProvider))

  def apply(name: String,settings: NetworkSettings,upnp: UPnP,peerHandlerManagerRef:ActorRef,timeProvider: NetworkTimeProvider)(implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, upnp,peerHandlerManagerRef,timeProvider), name)
}