package cluster

import akka.actor.{PoisonPill, Props, ActorSystem}
import akka.cluster.Cluster
import akka.contrib.pattern.{ClusterSingletonProxy, ClusterSingletonManager}
import akka.persistence.{SnapshotOffer, PersistentActor}
import akka.util.Timeout

import scala.concurrent.Await

case class OpenAccount(name: String)
case class CloseAccount(name: String)

case class AccountDB(accounts: Set[String]) {
  def add(name: String) = copy(accounts = accounts + name)
  def remove(name: String) = copy(accounts = accounts - name)
}

object AccountDB {
  def apply(): AccountDB = AccountDB(Set())
}

case class AccountOpened(name: String)
case class AccountClosed(name: String)

object AccountManager {
  def props(): Props = Props(new AccountManager)
}

class AccountManager extends PersistentActor {

  println(s"${self.path} started")

  val persistenceId = self.path.parent.name + "-" + self.path.name

  var db = AccountDB()

  def receiveRecover = {
    case SnapshotOffer(_, snapshot: AccountDB) => db = snapshot
    case evt: AccountOpened =>
      println(s"recovery $evt")
      update(evt)
    case evt: AccountClosed =>
      println(s"recovery $evt")
      update(evt)
  }

  def receiveCommand = {
    case OpenAccount(name) =>
      if (db.accounts.contains(name)) {
        sender() ! ()
      } else {
        persist(AccountOpened(name))(updateAndReply)
      }

    case CloseAccount(name) =>
      if (db.accounts.contains(name)) {
        persist(AccountClosed(name))(updateAndReply)
      } else {
        sender() ! akka.actor.Status.Failure(new Exception(s"account $name not found"))
      }
  }

  def update(evt: AccountOpened): Unit = {
    db = db.add(evt.name)
  }

  def update(evt: AccountClosed): Unit = {
    db = db.remove(evt.name)
  }

  def updateAndReply(evt: AccountOpened): Unit = {
    update(evt)
    sender() ! ()
  }

  def updateAndReply(evt: AccountClosed): Unit = {
    update(evt)
    sender() ! ()
  }

}

object Main {

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("test")

    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)

    val mgr = system.actorOf(ClusterSingletonManager.props(
      singletonProps = AccountManager.props(),
      singletonName = "singleton",
      terminationMessage = PoisonPill,
      role = None
    ), name = "manager")

    val proxy = system.actorOf(ClusterSingletonProxy.props(
      singletonPath = "/user/manager/singleton",
      role = None
    ), name = "proxy")

    import system.dispatcher
    import akka.pattern.ask
    import scala.concurrent.duration._

    implicit val timeout = Timeout(10.seconds)

    1 to 100 foreach { i =>
      val r = Await.result(proxy ? OpenAccount(s"User-$i"), timeout.duration)
      println(s"result = $r")
    }


  }
}
