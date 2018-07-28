package it.cwmp.client.view.room

import it.cwmp.client.utils.{LayoutRes, StringRes}
import it.cwmp.client.view._
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control._
import javafx.scene.layout.GridPane
import javafx.stage.Stage

trait RoomFXStrategy {
  def onCreate(name: String, nPlayer: Int): Unit

  def onEnterPrivate(idRoom: String): Unit

  def onEnterPublic(nPlayer: Int): Unit
}

object RoomFXController {
  def apply(strategy: RoomFXStrategy): RoomFXController = {
    require(strategy != null)
    new RoomFXController(strategy)
  }
}

class RoomFXController(strategy: RoomFXStrategy) extends FXInputController
  with FXView with FXInputChecks with FXAlerts with FXDialogs with FXRunOnUIThread {

  protected val layout: String = LayoutRes.roomManagerLayout
  protected val title: String = StringRes.roomManagerTitle
  protected val stage: Stage = new Stage
  protected val controller: FXInputController = this

  @FXML
  private var tfPrivateCreateRoomName: TextField = _
  @FXML
  private var spPrivateCreateNumPlayer: Spinner[Integer] = _
  @FXML
  private var tfPrivateEnterRoomID: TextField = _
  @FXML
  private var spPublicEnterNumPlayer: Spinner[Integer] = _
  @FXML
  private var btnPrivateCreate: Button = _
  @FXML
  private var btnPrivateReset: Button = _
  @FXML
  private var btnPrivateEnter: Button = _
  @FXML
  private var btnPublicEnter: Button = _

  //creare una stanza privata
  @FXML
  private def onClickCreate(): Unit = {
    Platform.runLater(() => {
      for (
        name <- getTextFieldValue(tfPrivateCreateRoomName, "Il nome non può essere vuoto"); // TODO parametrize input
        nPlayer <- getSpinnerFieldValue(spPrivateCreateNumPlayer, "Deve essere selezionato il numero di giocatori")
      ) yield {
        showLoadingDialog("Loading", "Stiamo creando la stanza privata")
        strategy.onCreate(name, nPlayer)
        btnPrivateCreate.setDisable(true)
        btnPrivateReset.setDisable(true)
      }
    })
  }

  @FXML
  private def onClickReset(): Unit = {
    Platform.runLater(() => {
      resetFields()
    })
  }

  override def resetFields(): Unit = {
    tfPrivateCreateRoomName setText ""
    spPrivateCreateNumPlayer getValueFactory() setValue 2
  }

  @FXML
  private def onClickEnter(): Unit = {
    Platform.runLater(() => {
      for (
        id_room <- getTextFieldValue(tfPrivateEnterRoomID, "L'ID della stanza non può essere vuoto") // TODO parametrize input
      ) yield {
        showLoadingDialog("Loading", "Stai per entrare nella stanza privata")
        strategy.onEnterPrivate(id_room)
        btnPrivateEnter.setDisable(true)
      }
    })
  }

  //Componenti tab stanze pubbliche
  @FXML
  private def onClickRoomPublic(): Unit = {
    Platform.runLater(() => {
      for (
        nPlayer <- getSpinnerFieldValue(spPublicEnterNumPlayer, "Deve essere selezionato il numero di giocatori") // TODO parametrize input
      ) yield {
        showLoadingDialog("Loading", "Stai per entrare in una stanza pubblica")
        strategy.onEnterPublic(nPlayer)
        btnPublicEnter.setDisable(true)
      }
    })
  }

  override def enableViewComponents(): Unit = {
    btnPrivateCreate.setDisable(false)
    btnPrivateReset.setDisable(false)
    btnPrivateEnter.setDisable(false)
    btnPublicEnter.setDisable(false)
  }

  /**
    * A method to show the token dialog to user
    *
    * @param roomToken the token that user should be able to copy
    */
  def showTokenDialog(roomToken: String): Unit =
    runOnUIThread(() =>
      showDialogWithContent("Private Room Token", "Questo è il token da usare per entrare nella stanza che hai creato",
        createRoomTokenDialogContent(roomToken)))

  /**
    * Creates the dialog content whit selectable token
    *
    * @param roomToken the token to show
    * @return the content of the dialog to show
    */
  private def createRoomTokenDialogContent(roomToken: String): Node = {
    val gridPane = new GridPane()
    gridPane.setHgap(10)
    gridPane.setVgap(10)

    val tokenLabel = new Label("Token: ")

    val tokenTextField = new TextField(roomToken)
    tokenTextField.setEditable(false)

    val okButton = new Button("OK")
    okButton.setOnAction((_) => hideDialog())

    gridPane.add(tokenLabel, 0, 0)
    gridPane.add(tokenTextField, 0, 1)
    gridPane.add(okButton, 1, 1)
    gridPane
  }
}
