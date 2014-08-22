package kafkaspike

import java.util.Properties
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props, Actor, ActorSystem}
import akka.serialization.SerializationExtension
import kafka.common.FailedToSendMessageException
import kafka.producer.{KeyedMessage, ProducerConfig, Producer}

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

object StatsActor {
  def props(statsInterval: FiniteDuration): Props = Props(new StatsActor(statsInterval))
}

class StatsActor(statsInterval: FiniteDuration) extends Actor {

  import context.dispatcher

  var counter = 0
  var error = 0

  override def preStart(): Unit = context.system.scheduler.schedule(0.seconds, statsInterval, self, "stats")

  def receive = {
    case "sent" => counter += 1
    case "error" => error += 1
    case "stats" =>
      val sentPerSec = counter / statsInterval.toUnit(TimeUnit.SECONDS)
      val errorsPerSec = error / statsInterval.toUnit(TimeUnit.SECONDS)
      counter = 0
      error = 0
      println(s"Send-rate = $sentPerSec (msg/sec), Error-rate = $errorsPerSec (msg/sec)  over last $statsInterval")
  }

}

object ProducerActor {
  def props(statsActor: ActorRef, tickInterval: FiniteDuration): Props =
    Props(new ProducerActor(statsActor, tickInterval))
}

case class ProducerMessage(path: String, counter: Int, extra: String)

class ProducerActor(
    statsActor: ActorRef,
    tickInterval: FiniteDuration)
  extends Actor {

  import context.dispatcher

  val producerProperties = new Properties()
  producerProperties.setProperty("metadata.broker.list", "127.0.0.1:9092")
  producerProperties.setProperty("request.required.acks", "1")
  producerProperties.setProperty("producer.type", "sync")

  val producerConfig = new ProducerConfig(producerProperties)
  val producer = new Producer[String, Array[Byte]](producerConfig)
  var counter = 0
  val topic = self.path.name

  val serializer = SerializationExtension(context.system)

  override def preStart(): Unit =
    context.system.scheduler.schedule(0.seconds, tickInterval, self, "tick")

  def receive = {
    case "tick" =>
      try {
        producer.send(new KeyedMessage[String, Array[Byte]](topic, serializer.serialize(getMessage).get))
        statsActor ! "sent"
      } catch {
        case e: FailedToSendMessageException => statsActor ! "error"
      } finally {
        counter += 1
      }
  }

  private def getMessage: ProducerMessage =
    ProducerMessage(self.path.toString, counter, (1 to 256).map(_ => "A").mkString(""))

}

object Main {

  final val NUM_PRODUCER_ACTORS = 300

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("test")

    val statsActor = system.actorOf(StatsActor.props(5.seconds), "stats")

    1 to NUM_PRODUCER_ACTORS foreach { i =>
      system.actorOf(ProducerActor.props(statsActor, 10.millis), s"producer-$i")
    }

  }

}
