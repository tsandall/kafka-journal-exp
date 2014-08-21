package domain

import akka.actor.Props
import akka.persistence.{SnapshotOffer, PersistentActor}

case class Account(balance: Int) {
  def debit(amount: Int) = copy(balance = balance - amount)
  def credit(amount: Int) = copy(balance = balance + amount)
}

object Account {
  def apply(): Account = Account(0)
}

sealed trait AccountCommand
case class DebitAccount(amount: Int) extends AccountCommand
case class AccountDebit(amount: Int) extends AccountCommand

case class CreditAccount(amount: Int)
case class AccountCredit(amount: Int)


object SimpleActor {
  def props(): Props = Props(new SimpleActor)
}

class SimpleActor extends PersistentActor {

  val persistenceId = self.path.parent.name + "-" + self.path.name

  var account = Account()

  def receiveRecover = {
    case msg @ SnapshotOffer(meta, snapshot: Account) =>
      println(s"recover $msg")
      account = snapshot
    case evt @ AccountDebit(amount) =>
      println(s"recover $evt")
      debit(evt)
    case evt @ AccountCredit(amount) =>
      println(s"recover $evt")
      credit(evt)
  }

  def receiveCommand = {

    case DebitAccount(amount) =>
      if (account.balance >= amount) {
        persist(AccountDebit(amount))(debit)
      }

    case msg @ CreditAccount(amount) =>
      persist(AccountCredit(amount))(credit)

    case "print" => println(account)

    case "snap" => saveSnapshot(account)
  }

  private def debit(evt: AccountDebit): Unit = account = account.debit(evt.amount)
  private def credit(evt: AccountCredit): Unit = account = account.credit(evt.amount)

}

