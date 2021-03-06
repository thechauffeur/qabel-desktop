package de.qabel.desktop.ui.accounting.qrcode;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import de.qabel.core.config.Identity;
import de.qabel.core.config.factory.DropUrlGenerator;
import de.qabel.desktop.ui.AbstractController;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import net.glxn.qrgen.core.image.ImageType;
import net.glxn.qrgen.javase.QRCode;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;

public class QRCodeController extends AbstractController {
    @FXML
    public Pane qrcode;

    @FXML
    private Label labelClient;

    @FXML
    private Label labelUrl;

    @FXML
    private Label labelKey;

    @FXML
    private ImageView imageQrCode;

    @Inject
    private Pane layoutWindow;

    @Inject
    private Identity identity;

    private String dropUrl;

    private String textQRCode;

    public void buttonClosePopup() {
        hide();
    }

    public void show() {
        setIdentity();
        Platform.runLater(() -> qrcode.setVisible(true));
    }

    private void hide() {
        Platform.runLater(() -> qrcode.setVisible(false));
    }

    public boolean isVisible() {
        return qrcode.isVisible();
    }

    private void setIdentity() {
        dropUrl = identity.getHelloDropUrl().toString();
        textQRCode = "QABELCONTACT\n"
            + identity.getAlias() + "\n"
            + dropUrl + "\n"
            + identity.getEcPublicKey().getReadableKeyIdentifier();

        labelClient.setText(identity.getAlias());
        labelUrl.setText(dropUrl);
        labelKey.setText(identity.getEcPublicKey().getReadableKeyIdentifier());
        generateQRCode(textQRCode);
    }

    protected void generateQRCode(String qrcode) {
        final byte[] imageBytes;
        QRCode.from(qrcode).withErrorCorrection(ErrorCorrectionLevel.L);
        imageBytes = QRCode.from(qrcode).withSize(300, 250).to(ImageType.PNG).stream().toByteArray();
        Image qrCodeGraphics = new Image(new ByteArrayInputStream(imageBytes));
        imageQrCode.setImage(qrCodeGraphics);
    }

    protected String getDropUrl() {
        return dropUrl;
    }

    protected String getPublicKey() {
        return identity.getEcPublicKey().getReadableKeyIdentifier();
    }

    protected String getTextQRCode() {
        return textQRCode;
    }

    protected String getAlias() {
        return identity.getAlias();
    }

}
