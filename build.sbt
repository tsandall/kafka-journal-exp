name := "kafka-journal-exp"

version := "1.0"

scalaVersion := "2.10.3"

resolvers += "local ivy repo" at "file://"+Path.userHome.absolutePath+"/.ivy/local"

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.4"

libraryDependencies += "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.4"

libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.3.4"

libraryDependencies += "com.typesafe.akka" %% "akka-contrib" % "2.3.4"

libraryDependencies += "com.github.krasserm" %% "akka-persistence-kafka" % "0.3-SNAPSHOT"

libraryDependencies += "com.github.krasserm" %% "akka-persistence-cassandra" % "0.3.3"