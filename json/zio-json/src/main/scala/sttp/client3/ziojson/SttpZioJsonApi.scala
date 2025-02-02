package sttp.client3.ziojson

import sttp.client3.internal.Utf8
import sttp.client3.json.RichResponseAs
import sttp.client3.{
  BodySerializer,
  DeserializationException,
  HttpError,
  IsOption,
  JsonInput,
  ResponseAs,
  ResponseException,
  ShowError,
  StringBody,
  asString,
  asStringAlways
}
import sttp.model.MediaType

trait SttpZioJsonApi extends SttpZioJsonApiExtensions {
  import zio.json._
  private[ziojson] implicit val stringShowError: ShowError[String] = t => t

  implicit def zioJsonBodySerializer[B: JsonEncoder]: BodySerializer[B] =
    b => StringBody(b.toJson, Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: JsonDecoder: IsOption]: ResponseAs[Either[ResponseException[String, String], B], Any] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonDecoder: IsOption]: ResponseAs[Either[DeserializationException[String], B], Any] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: JsonDecoder: IsOption, B: JsonDecoder: IsOption]
      : ResponseAs[Either[ResponseException[E, String], B], Any] =
    asJson[B].mapLeft {
      case HttpError(e, code) => deserializeJson[E].apply(e).fold(DeserializationException(e, _), HttpError(_, code))
      case de @ DeserializationException(_, _) => de
    }.showAsJsonEither

  def deserializeJson[B: JsonDecoder: IsOption]: String => Either[String, B] =
    JsonInput.sanitize[B].andThen(_.fromJson[B])
}
