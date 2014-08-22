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

sealed trait AccountCommand {
  val id: String
}

case class GetBalance(id: String) extends AccountCommand
case class DebitAccount(id: String, amount: Int) extends AccountCommand
case class CreditAccount(id: String, amount: Int) extends AccountCommand

case object AccountCommandExecuted

sealed trait AccountEvent

case class AccountDebit(amount: Int) extends AccountEvent
case class AccountCredit(amount: Int) extends AccountEvent

object AccountActor {
  def props(): Props = Props(new AccountActor)
}

class AccountActor extends PersistentActor {

  val persistenceId = self.path.name

  var account = Account()

  def receiveRecover = {
    case msg @ SnapshotOffer(meta, snapshot: Account) => account = snapshot
    case evt @ AccountDebit(amount) => debit(evt)
    case evt @ AccountCredit(amount) => credit(evt)
  }

  def receiveCommand = {
    case DebitAccount(_, amount) => persist(AccountDebit(amount))(debitAndReply)
    case CreditAccount(_, amount) => persist(AccountCredit(amount))(creditAndReply)
    case GetBalance(name) => sender() ! account.balance
    case "snap" => saveSnapshot(account)
  }

  private def debitAndReply(evt: AccountDebit): Unit = {
    debit(evt)
    sender() ! AccountCommandExecuted
  }

  private def debit(evt: AccountDebit): Unit = account = account.debit(evt.amount)

  private def creditAndReply(evt: AccountCredit): Unit = {
    credit(evt)
    sender() ! AccountCommandExecuted
  }

  private def credit(evt: AccountCredit): Unit = account = account.credit(evt.amount)

}

