package it.cwmp.controller.rooms

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod._
import io.vertx.scala.ext.web.client.{HttpResponse, WebClient}
import it.cwmp.controller.ApiClient
import it.cwmp.model.{Address, Room}
import it.cwmp.testing.HttpMatchers
import it.cwmp.testing.server.rooms.RoomsWebServiceTesting
import org.scalatest.Assertion

import scala.concurrent.Future

/**
  * Test class for RoomsServiceVerticle
  *
  * @author Enrico Siboni
  */
class RoomsServiceVerticleTest extends RoomsWebServiceTesting with HttpMatchers with ApiClient {

  import RoomsApiWrapper._

  private implicit var webClient: WebClient = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    webClient = createWebClient("localhost", DEFAULT_PORT, vertx)
  }

  override protected def privateRoomCreationTests(roomName: String, playersNumber: Int): Unit = {
    val creationApi = API_CREATE_PRIVATE_ROOM_URL

    it("should succeed when the user is authenticated and input is valid") {
      createPrivateRoom(roomName, playersNumber) flatMap (res => assert(res.statusCode() == 201 && res.bodyAsString().isDefined))
    }

    describe("should fail") {
      it("if no room is sent with request") {
        createClientRequestWithToken(POST, creationApi) flatMap (_.sendFuture()) httpStatusCodeEquals 400
      }
      it("if body is malformed") {
        createClientRequestWithToken(POST, creationApi) flatMap (_.sendJsonFuture("Ciao")) httpStatusCodeEquals 400
      }
      it("if the roomName sent is empty") {
        createPrivateRoom("", playersNumber) httpStatusCodeEquals 400
      }
      it("if the playersNumber isn't valid") {
        createPrivateRoom(roomName, 0) httpStatusCodeEquals 400
      }
    }
  }

  override protected def privateRoomEnteringTests(roomName: String, playersNumber: Int): Unit = {
    it("should succeed when the user is authenticated, input is valid and room non full") {
      createAPrivateRoomAndGetID(roomName, playersNumber)
        .flatMap(roomID => (enterPrivateRoom(roomID, myFirstAuthorizedUser, notificationAddress) httpStatusCodeEquals 200)
          .flatMap(cleanUpRoom(roomID, _)))
    }

    describe("should fail") {
      onWrongRoomID(enterPrivateRoom(_, myFirstAuthorizedUser, notificationAddress))

      it("if address not provided") {
        createClientRequestWithToken(PUT, API_ENTER_PRIVATE_ROOM_URL) flatMap (_.sendFuture()) httpStatusCodeEquals 400
      }
      it("if address message malformed") {
        createClientRequestWithToken(PUT, API_ENTER_PRIVATE_ROOM_URL) flatMap (_.sendJsonFuture("Ciao")) httpStatusCodeEquals 400
      }
      it("if user is already inside a room") {
        createAPrivateRoomAndGetID(roomName, playersNumber)
          .flatMap(roomID => enterPrivateRoom(roomID, myFirstAuthorizedUser, notificationAddress)
            .flatMap(_ => enterPrivateRoom(roomID, myFirstAuthorizedUser, notificationAddress) httpStatusCodeEquals 400)
            .flatMap(cleanUpRoom(roomID, _)))
      }
      it("if the room was already filled") {
        val playersNumber = 2
        createAPrivateRoomAndGetID(roomName, playersNumber)
          .flatMap(roomID => enterPrivateRoom(roomID, myFirstAuthorizedUser, notificationAddress)
            .flatMap(_ => enterPrivateRoom(roomID, mySecondAuthorizedUser, notificationAddress)(webClient, mySecondCorrectToken))
            .flatMap(_ => enterPrivateRoom(roomID, myThirdAuthorizedUser, notificationAddress)(webClient, myThirdCorrectToken))) httpStatusCodeEquals 404
      }
    }
  }

  override protected def privateRoomInfoRetrievalTests(roomName: String, playersNumber: Int): Unit = {
    it("should succeed if roomId is correct") {
      var roomJson = ""
      createAPrivateRoomAndGetID(roomName, playersNumber)
        .flatMap(roomID => privateRoomInfo(roomID)
          .map(response => {
            import Room.Converters._
            roomJson = Room(roomID, roomName, playersNumber, Seq()).toJson.encode()
            response
          }))
        .flatMap(res => assert(res.statusCode() == 200 && res.bodyAsString().get == roomJson))
    }

    describe("should fail") {
      onWrongRoomID(privateRoomInfo)
    }
  }

  override protected def privateRoomExitingTests(roomName: String, playersNumber: Int): Unit = {
    it("should succeed if roomID is correct and user inside") {
      createAPrivateRoomAndGetID(roomName, playersNumber)
        .flatMap(roomID => enterPrivateRoom(roomID, myFirstAuthorizedUser, notificationAddress)
          .flatMap(_ => exitPrivateRoom(roomID))) httpStatusCodeEquals 200
    }
    it("user should not be inside after it") {
      createAPrivateRoomAndGetID(roomName, playersNumber)
        .flatMap(roomID => enterPrivateRoom(roomID, myFirstAuthorizedUser, notificationAddress)
          .flatMap(_ => exitPrivateRoom(roomID))
          .flatMap(_ => privateRoomInfo(roomID)))
        .flatMap(res => assert(res.statusCode() == 200 && !res.bodyAsString().get.contains(myFirstAuthorizedUser.username)))
    }

    describe("should fail") {
      onWrongRoomID(exitPrivateRoom)

      it("if user is not inside the room") {
        createAPrivateRoomAndGetID(roomName, playersNumber) flatMap (roomID => exitPrivateRoom(roomID)) httpStatusCodeEquals 400
      }
      it("if user is inside another room") {
        createAPrivateRoomAndGetID(roomName, playersNumber)
          .flatMap(roomID => createAPrivateRoomAndGetID(roomName, playersNumber)
            .flatMap(otherRoomID => enterPrivateRoom(roomID, myFirstAuthorizedUser, notificationAddress)
              .flatMap(_ => exitPrivateRoom(otherRoomID) httpStatusCodeEquals 400))
            .flatMap(cleanUpRoom(roomID, _)))
      }
    }
  }

  override protected def publicRoomEnteringTests(playersNumber: Int): Unit = {
    describe("should succeed") {
      it("if room with such players number exists") {
        enterPublicRoom(playersNumber, myFirstAuthorizedUser, notificationAddress) httpStatusCodeEquals 200 flatMap (cleanUpRoom(playersNumber, _))
      }
      it("and the user should be inside it") {
        (enterPublicRoom(playersNumber, myFirstAuthorizedUser, notificationAddress) flatMap (_ => publicRoomInfo(playersNumber)))
          .flatMap(res => assert(res.statusCode() == 200 && res.bodyAsString().get.contains(myFirstAuthorizedUser.username)))
          .flatMap(cleanUpRoom(playersNumber, _))
      }
      it("even when the room was filled in past") {
        enterPublicRoom(2, myFirstAuthorizedUser, notificationAddress)
          .flatMap(_ => enterPublicRoom(2, mySecondAuthorizedUser, notificationAddress)(webClient, mySecondCorrectToken))
          .flatMap(_ => enterPublicRoom(2, myThirdAuthorizedUser, notificationAddress)(webClient, myThirdCorrectToken))
          .flatMap(_ => publicRoomInfo(2))
          .flatMap(res => assert(res.statusCode() == 200 &&
            !res.bodyAsString().get.contains(myFirstAuthorizedUser.username) &&
            !res.bodyAsString().get.contains(mySecondAuthorizedUser.username) &&
            res.bodyAsString().get.contains(myThirdAuthorizedUser.username)))
          .flatMap(cleanUpRoom(playersNumber, _)(myThirdCorrectToken))
      }
    }

    describe("should fail") {
      onWrongPlayersNumber(enterPublicRoom(_, myFirstAuthorizedUser, notificationAddress))

      it("if address not provided") {
        createClientRequestWithToken(PUT, API_ENTER_PUBLIC_ROOM_URL) flatMap (_.sendFuture()) httpStatusCodeEquals 400
      }
      it("if address message malformed") {
        createClientRequestWithToken(PUT, API_ENTER_PUBLIC_ROOM_URL) flatMap (_.sendJsonFuture("Ciao")) httpStatusCodeEquals 400
      }
      it("if same user is already inside a room") {
        enterPublicRoom(2, myFirstAuthorizedUser, notificationAddress)
          .flatMap(_ => enterPublicRoom(3, myFirstAuthorizedUser, notificationAddress))
          .httpStatusCodeEquals(400) flatMap (cleanUpRoom(playersNumber, _))
      }
    }
  }

  override protected def publicRoomListingTests(playersNumber: Int): Unit = {
    it("should be nonEmpty") {
      listPublicRooms() flatMap (res => assert(res.statusCode() == 200 && res.bodyAsString().get.nonEmpty))
    }
  }

  override protected def publicRoomInfoRetrievalTests(playersNumber: Int): Unit = {
    it("should succeed if provided playersNumber is correct") {
      publicRoomInfo(playersNumber) httpStatusCodeEquals 200
    }
    it("should show entered players") {
      (enterPublicRoom(playersNumber, myFirstAuthorizedUser, notificationAddress) flatMap (_ => publicRoomInfo(playersNumber)))
        .flatMap(res => assert(res.statusCode() == 200 && res.bodyAsString().get.contains(myFirstAuthorizedUser.username)))
        .flatMap(cleanUpRoom(playersNumber, _))
    }

    describe("should fail") {
      onWrongPlayersNumber(publicRoomInfo)
    }
  }

  override protected def publicRoomExitingTests(playersNumber: Int): Unit = {
    it("should succeed if players number is correct and user is inside") {
      (enterPublicRoom(playersNumber, myFirstAuthorizedUser, notificationAddress) flatMap (_ => exitPublicRoom(playersNumber))) httpStatusCodeEquals 200
    }
    it("user should not be inside after it") {
      enterPublicRoom(playersNumber, myFirstAuthorizedUser, notificationAddress)
        .flatMap(_ => exitPublicRoom(playersNumber))
        .flatMap(_ => publicRoomInfo(playersNumber))
        .flatMap(res => assert(res.statusCode() == 200 && !res.bodyAsString().get.contains(myFirstAuthorizedUser.username)))
    }

    describe("should fail") {
      onWrongPlayersNumber(exitPublicRoom)

      it("if user is not inside the room") {
        exitPublicRoom(playersNumber) httpStatusCodeEquals 400
      }
      it("if user is inside another room") {
        (enterPublicRoom(2, myFirstAuthorizedUser, notificationAddress) flatMap (_ => exitPublicRoom(3)))
          .httpStatusCodeEquals(400) flatMap (cleanUpRoom(playersNumber, _))
      }
    }
  }

  describe("Room Api") {
    val roomApiInteractions = {
      Seq(
        (POST, API_CREATE_PRIVATE_ROOM_URL),
        (PUT, API_ENTER_PRIVATE_ROOM_URL),
        (GET, API_PRIVATE_ROOM_INFO_URL),
        (DELETE, API_EXIT_PRIVATE_ROOM_URL),
        (GET, API_LIST_PUBLIC_ROOMS_URL),
        (PUT, API_ENTER_PUBLIC_ROOM_URL),
        (GET, API_PUBLIC_ROOM_INFO_URL),
        (DELETE, API_EXIT_PUBLIC_ROOM_URL))
    }

    describe("should fail") {
      for (apiCall <- roomApiInteractions) {
        it(s"if token not provided, doing ${apiCall._1.toString} on ${apiCall._2}") {
          (createClientRequest(webClient, apiCall._1, apiCall._2) flatMap (_.sendFuture())) httpStatusCodeEquals 400
        }
        it(s"if token wrong, doing ${apiCall._1.toString} on ${apiCall._2}") {
          createClientRequestWithToken(apiCall._1, apiCall._2)(webClient, "token")
            .flatMap(_.sendFuture()) httpStatusCodeEquals 401
        }
        it(s"if token isn't valid, doing ${apiCall._1.toString} on ${apiCall._2}") {
          createClientRequestWithToken(apiCall._1, apiCall._2)(webClient, "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6InRpemlvIn0.f6eS98GeBmPau4O58NwQa_XRu3Opv6qWxYISWU78F68")
            .flatMap(_.sendFuture()) httpStatusCodeEquals 401
        }
      }
    }
  }

  /**
    * Describes the behaviour of private api when the roomID is wrong
    */
  override protected def onWrongRoomID(apiCall: String => Future[_]): Unit = {
    it("if roomID is empty") {
      apiCall("").mapTo[HttpResponse[Buffer]] httpStatusCodeEquals 400
    }
    it("if provided roomID is not present") {
      apiCall("1234214").mapTo[HttpResponse[Buffer]] httpStatusCodeEquals 404
    }
  }

  /**
    * Describes the behaviour of public api when called with wrong players number
    */
  override protected def onWrongPlayersNumber(apiCall: (Int) => Future[_]): Unit = {
    it("if players number provided less than 2") {
      apiCall(0).mapTo[HttpResponse[Buffer]] httpStatusCodeEquals 400
    }
    it("if room with such players number doesn't exist") {
      apiCall(20).mapTo[HttpResponse[Buffer]] httpStatusCodeEquals 404
    }
  }

  /**
    * Shorthand to create a room and get it's id from response
    */
  private def createAPrivateRoomAndGetID(roomName: String, playersNumber: Int) = {
    createPrivateRoom(roomName, playersNumber) map (_.bodyAsString().get)
  }

  /**
    * Cleans up the provided room, exiting the user with passed token
    */
  private def cleanUpRoom(roomID: String, assertion: Assertion)(implicit userToken: String) =
    exitPrivateRoom(roomID)(webClient, userToken) map (_ => assertion)

  /**
    * Cleans up the provided public room, exiting player with passed token
    */
  private def cleanUpRoom(playersNumber: Int, assertion: Assertion)(implicit userToken: String) =
    exitPublicRoom(playersNumber)(webClient, userToken) map (_ => assertion)
}