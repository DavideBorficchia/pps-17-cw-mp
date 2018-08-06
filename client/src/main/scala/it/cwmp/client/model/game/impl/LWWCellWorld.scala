package it.cwmp.client.model.game.impl

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator.{Changed, Update}
import akka.cluster.ddata._
import it.cwmp.client.model.AkkaDistributedState
import it.cwmp.client.model.AkkaDistributedState.UpdateState
import it.cwmp.client.model.game.impl.LWWCellWorld.DISTRIBUTED_KEY_NAME

/**
  * Distributed representation of the world where "Latest Write Wins"
  *
  * @param onWorldUpdate   the strategy to adopt on world changes
  * @param replicatorActor the actor that will distribute the data
  * @param cluster         the cluster where this distributed data are exchanged
  * @author Eugenio Pierfederici
  * @author contributor Enrico Siboni
  */
case class LWWCellWorld(onWorldUpdate: CellWorld => Unit)
                       (implicit replicatorActor: ActorRef, cluster: Cluster) extends AkkaDistributedState[CellWorld] {

  override protected val distributedKey: LWWRegisterKey[CellWorld] =
    LWWRegisterKey[CellWorld](DISTRIBUTED_KEY_NAME)

  override def initialize(initialState: CellWorld): Unit = writeDistributed(initialState)

  override protected def passiveBehaviour: Receive = {
    // Called when notified of the distributed data change
    case msg@Changed(`distributedKey`) =>
      log.debug("Being notified that distributed state has changed")
      onWorldUpdate(msg.get(distributedKey).value)
  }

  override protected def activeBehaviour: Receive = {
    case UpdateState(state: CellWorld) =>
      log.debug("Updating distributed state")
      writeDistributed(state)
  }

  /**
    * Handle method to do a distributed write
    *
    * @param state the state to write
    */
  private def writeDistributed(state: CellWorld): Unit =
    replicatorActor ! Update(distributedKey, LWWRegister[CellWorld](state), consistencyPolicy)(_.withValue(state))
}

/**
  * Companion Object
  */
object LWWCellWorld {
  private val DISTRIBUTED_KEY_NAME = "distributedKey"
}
