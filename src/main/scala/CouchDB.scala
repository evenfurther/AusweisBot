import java.time.{ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.model.headers.{Accept, ETag}
import akka.http.scaladsl.model.{Uri, _}
import akka.http.scaladsl.{Http, model}
import models.DBProtocol._
import models.PersonalData
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// Mini CouchDB interface implementing only document saving, querying and deleting.

class CouchDB(context: ActorContext[DBControl], dbUri: Uri)
    extends AbstractBehavior[DBControl](context) {

  import CouchDB._

  private[this] implicit val system: ActorSystem = context.system.toClassic
  private[this] implicit val ec: ExecutionContext = context.executionContext
  private[this] val http = Http()
  private[this] def docUri(id: Int): Uri =
    dbUri.withPath(dbUri.path ++ Uri.Path(docId(id)))

  override def onMessage(msg: DBControl): Behavior[DBControl] =
    msg match {
      case Save(id, data) =>
        http
          .singleRequest(
            HttpRequest(
              HttpMethods.PUT,
              docUri(id),
              headers = headers,
              entity = model.HttpEntity(
                ContentTypes.`application/json`,
                Json.stringify(
                  Json.toJsObject(data) ++ Json.obj(
                    "insertedAt" -> ZonedDateTime
                      .now(ZoneId.of("UTC"))
                      .toInstant()
                  )
                )
              )
            )
          )
          .foreach(_.discardEntityBytes())
        Behaviors.same
      case Load(id, replyTo) =>
        context.pipeToSelf(
          http
            .singleRequest(
              HttpRequest(HttpMethods.GET, docUri(id), headers = headers)
            )
            .flatMap { response =>
              if (response.status.isSuccess) {
                response.entity
                  .toStrict(5.seconds)
                  .map(body =>
                    Some(
                      Json.parse(body.data.toArray).validate[PersonalData].get
                    )
                  )
              } else {
                response.discardEntityBytes()
                Future.successful(None)
              }
            }
        ) {
          case Success(reply) => SendReply(replyTo, reply)
          case Failure(_)     => SendReply(replyTo, None)
        }
        Behaviors.same
      case Delete(id) =>
        // To prevent a conflict, CouchDB requires the revision string before
        // deleting a document. This revision string can be obtained from a
        // HEAD request ETag header.
        val uri = docUri(id)
        http
          .singleRequest(HttpRequest(HttpMethods.HEAD, uri, headers = headers))
          .foreach { response =>
            response.discardEntityBytes()
            if (response.status.isSuccess) {
              response.header[ETag].foreach { etag =>
                http
                  .singleRequest(
                    HttpRequest(
                      HttpMethods.DELETE,
                      uri.withQuery(
                        Uri.Query(
                          "rev" -> etag
                            .value()
                            .stripPrefix("\"")
                            .stripSuffix("\"")
                        )
                      ),
                      headers = headers
                    )
                  )
                  .foreach(_.discardEntityBytes())
              }
            }
          }
        Behaviors.same
      case SendReply(replyTo, reply) =>
        replyTo ! reply
        Behaviors.same
    }

}

object CouchDB {
  def apply(dbUri: Uri): Behavior[DBCommand] = {
    val baseUri = if (dbUri.path.endsWith("/")) { dbUri }
    else { dbUri.withPath(dbUri.path ++ Uri.Path.SingleSlash) }
    Behaviors.setup[DBControl](new CouchDB(_, baseUri)).narrow[DBCommand]
  }

  private val headers = List(Accept(MediaTypes.`application/json`))

  private def docId(id: Int) = s"telegram-user$id"
}
