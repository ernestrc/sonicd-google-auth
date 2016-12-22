package build.unstable.sonicd.gauth

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import build.unstable.sonic.model.{ApiUser, AuthConfig, ExternalAuthProvider}
import build.unstable.tylog.{TypedLogging, Variation}
import org.slf4j.event.Level
import spray.json.RootJsonFormat

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GoogleAuthProviderLogging extends TypedLogging {

  sealed trait CallType

  case object GAuthGetTokenInfo extends CallType

  type TraceID = String
}

class GoogleAuthProvider extends ExternalAuthProvider {

  import GoogleAuthProvider._
  import GoogleAuthProviderLogging._
  import build.unstable.sonic.JsonProtocol._
  import spray.json._

  override def validate(auth: AuthConfig, system: ActorSystem, traceId: String): Future[ApiUser] = {
    import system.dispatcher

    implicit val sys: ActorSystem = system
    implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system))

    log.tylog(Level.DEBUG, traceId, GAuthGetTokenInfo, Variation.Attempt, "validating google oauth2 token")

    val user = try {
      val token = auth.config("token").convertTo[String]
      val url = s"https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=$token"
      val req = HttpRequest(uri = Uri(url))

      log.debug("authenticating token with {}", url)

      Http(system).singleRequest(req).flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) ⇒
          entity.toStrict(40.seconds)
            .map { entity ⇒
              val tokenInfo = entity.data.utf8String.parseJson.convertTo[TokenInfo]
              // write mode only if we know who is it
              val mode = if (tokenInfo.email.isDefined) AuthConfig.Mode.ReadWrite else AuthConfig.Mode.Read
              // TODO validate should be given .conf config to properly determine this values
              ApiUser(tokenInfo.email.getOrElse("unknown-user"), 20, mode, None)
            }
        case HttpResponse(status, _, entity, _) ⇒
          entity.toStrict(40.seconds).flatMap(d ⇒ Future.failed(new Exception(d.data.utf8String)))
      }
    } catch {
      case e: Exception ⇒ Future.failed(e)
    }

    user.andThen {
      case Success(s) ⇒
        log.tylog(Level.DEBUG, traceId, GAuthGetTokenInfo, Variation.Success, "authenticated user {}", s.user)
      case Failure(e) ⇒
        log.tylog(Level.DEBUG, traceId, GAuthGetTokenInfo, Variation.Failure(e), "failed to validate token")
    }
  }
}

object GoogleAuthProvider {

  import build.unstable.sonic.JsonProtocol._

  // https://developers.google.com/identity/sign-in/web/backend-auth
  case class TokenInfo(iss: String, sub: String, azp: String, aud: String, iat: String, exp: String,
                       email: Option[String], email_verified: Option[String], name: Option[String],
                       picture: Option[String], given_name: Option[String], family_name: Option[String],
                       locale: Option[String])

  implicit val tokenInfoJsonFormat: RootJsonFormat[TokenInfo] = jsonFormat13(TokenInfo.apply)

}
