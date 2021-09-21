Docker / packageName := "registry.gitlab.com/huron/huron/huron-web"
Docker / maintainer := "Pawel <inne.poczta@gmail.com>"

dockerBaseImage := "openjdk:11-jre-slim"
dockerExposedPorts ++= Seq(8080, 808)

dockerUpdateLatest := true
