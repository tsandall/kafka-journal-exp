package cluster

import java.util.Random
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.{ShardRegion, ClusterSharding}
import domain._

import scala.concurrent.duration._

object AccountManager {
  def props(): Props = Props(new AccountManager)
}

object AccountSharding {

  final val TYPE_NAME = "account"

  val idExtractor: ShardRegion.IdExtractor = {
    case msg: AccountCommand => (msg.id, msg)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case msg: AccountCommand => hashId(msg.id)
  }

  def hashId(x: String) = (math.abs(x.hashCode) % 100).toString
}

class AccountManager extends Actor {

  val shardRegion = ClusterSharding(context.system).start(
    typeName = AccountSharding.TYPE_NAME,
    entryProps = Some(SimpleActor.props()),
    shardResolver = AccountSharding.shardResolver,
    idExtractor = AccountSharding.idExtractor
  )

  var committed = 0
  var forwarded = 0

  import context.dispatcher

  val interval = 5.seconds

  override def preStart(): Unit = context.system.scheduler.schedule(0.seconds, interval, self, "tick")

  def receive = {

    case msg: AccountCommand =>
      forwarded += 1
      shardRegion ! msg

    case AccountCommandReply =>
      committed += 1

    case "tick" =>
      val intervalInSecs = interval.toUnit(TimeUnit.SECONDS)
      val forwardRate = forwarded / intervalInSecs
      val commitRate = committed / intervalInSecs

      println(s"Forward-rate = $forwardRate (per sec), Commit-rate = $commitRate (per sec) (last $interval)")
      committed = 0
      forwarded = 0
  }

}

object TrafficActor {

  def props(proxy: ActorRef, tickInterval: FiniteDuration): Props = Props(new TrafficActor(proxy, tickInterval))

}

class TrafficActor(proxy: ActorRef, tickInterval: FiniteDuration) extends Actor {

  import context.dispatcher

  final val MAX_ACCOUNTS = 100
  final val MAX_CREDIT_AMOUNT = 1000
  final val MAX_DEBIT_ACCOUNT = 950
  val rng = new Random()

  override def preStart(): Unit = context.system.scheduler.schedule(0.seconds, tickInterval, self, "tick")

  def receive = {
    case "tick" => tick()
  }

  def tick(): Unit = {
    val msg = if (rng.nextBoolean()) {
      val amount = rng.nextInt(MAX_CREDIT_AMOUNT)
      val id = rng.nextInt(MAX_ACCOUNTS)
      CreditAccount(id.toString, amount)
    } else {
      val amount = rng.nextInt(MAX_DEBIT_ACCOUNT)
      val id = rng.nextInt(MAX_ACCOUNTS)
      DebitAccount(id.toString, amount)
    }
    proxy ! msg
  }
}

object Main {

  final val MAX_TRAFFIC_ACTORS = 300

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("test")

    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)

    val proxy = system.actorOf(AccountManager.props(), name = "manager")

    1 to MAX_TRAFFIC_ACTORS foreach { d => system.actorOf(TrafficActor.props(proxy, 10.millis), s"traffic-$d") }

  }
}
