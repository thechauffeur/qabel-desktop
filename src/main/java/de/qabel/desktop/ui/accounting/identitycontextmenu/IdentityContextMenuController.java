package de.qabel.desktop.ui.accounting.identitycontextmenu;

import de.qabel.core.config.Identity;
import de.qabel.desktop.ui.AbstractController;
import de.qabel.desktop.ui.accounting.identity.IdentityEditController;
import de.qabel.desktop.ui.accounting.identity.IdentityEditView;
import de.qabel.desktop.ui.accounting.qrcode.QRCodeController;
import de.qabel.desktop.ui.accounting.qrcode.QRCodeView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.PopOver;

import javax.inject.Inject;
import java.net.URL;
import java.util.ResourceBundle;

public class IdentityContextMenuController extends AbstractController implements Initializable {

    private ResourceBundle resourceBundle;

    @Inject
    private Identity identity;

    @Inject
    Pane layoutWindow;

    @FXML
    AnchorPane identityContextMenu;

    @FXML
    VBox contextMenu;

    private QRCodeView qrcodeView;
    QRCodeController qrcodeController;

    private IdentityEditView identityEditView;
    IdentityEditController identityEditController;
    PopOver popOver;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        resourceBundle = resources;
        initializePopOver();

    }

    public void openMenu(double coordPopOverX, double coordPopOverY) {
        Platform.runLater(() -> popOver.show(contextMenu, coordPopOverX, coordPopOverY));
    }

    void openMenu() {
        Platform.runLater(() -> popOver.show(contextMenu));
    }

    public void closeMenu() {
//        identityContextMenu.setVisible(false);
        Platform.runLater(() -> popOver.hide());
    }

    @FXML
    public void openIdentityEdit() {
        closeMenu();
        createIdentityEdit(identityContextMenu);
        Platform.runLater(() -> identityEditController.show());
    }

    @FXML
    public void openQRCode() {
        closeMenu();
        createQrCodePopup(identityContextMenu);
        Platform.runLater(() -> qrcodeController.showPopup());
    }

    private void initializePopOver() {
        if (popOver == null) {
            popOver = new PopOver();
            popOver.setArrowLocation(PopOver.ArrowLocation.TOP_RIGHT);
            popOver.setContentNode(new Pane(layoutWindow));
            popOver.setAutoFix(true);
            popOver.setAutoHide(true);
            popOver.setHideOnEscape(true);
            popOver.setDetachable(false);
        }
    }


    private void createQrCodePopup(Pane container) {
        if (qrcodeView == null) {
            qrcodeView = new QRCodeView(generateInjection("identity", identity));
            qrcodeView.getView(container.getChildren()::add);
            qrcodeController = (QRCodeController) qrcodeView.getPresenter();
        }
    }


    IdentityEditView createIdentityEdit(Pane container) {
        if (identityEditView == null) {
            identityEditView = new IdentityEditView(generateInjection("identity", identity));
            identityEditView.getView(container.getChildren()::add);
            identityEditController = (IdentityEditController) identityEditView.getPresenter();
        }
        return identityEditView;
    }
}
