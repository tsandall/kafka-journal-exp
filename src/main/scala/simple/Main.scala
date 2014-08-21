package simple

import akka.actor.ActorSystem
import domain.{DebitAccount, CreditAccount, SimpleActor}

import scala.util.Random

object Main {

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("test")

    val ref = system.actorOf(SimpleActor.props(), name = "simpleActor")

    val random = new Random()
    var lastTimestamp = System.currentTimeMillis()

    while (true) {

      val currentTimestamp = System.currentTimeMillis()
      if (currentTimestamp - lastTimestamp > 5000) {
        ref ! "snap"
        ref ! "print"
        lastTimestamp = currentTimestamp
      }

      val msg = if (random.nextBoolean()) {
        val amount = random.nextInt(1000)
        CreditAccount(amount)
      } else {
        val amount = random.nextInt(900)
        DebitAccount(amount)
      }
      ref ! msg
      Thread.sleep(10)
    }
  }

}
