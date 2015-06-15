import play.api.http.DefaultHttpErrorHandler
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.routing.Router
import play.core.SourceMapper
import scala.concurrent._

class ErrorHandler(
    env: Environment,
    config: Configuration,
    sourceMapper: Option[SourceMapper],
    router: Router) extends DefaultHttpErrorHandler(env, config, sourceMapper, Option(router)) {

  override def onProdServerError(request: RequestHeader, exception: UsefulException) = {
    Future.successful(
      InternalServerError("A server error occurred: " + exception.getMessage)
    )
  }

  override def onForbidden(request: RequestHeader, message: String) = {
    Future.successful(
      Forbidden("You're not allowed to access this resource.")
    )
  }
}