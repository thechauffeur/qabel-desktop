package de.qabel.desktop.ui.remotefs;

import de.qabel.core.config.Contact;
import de.qabel.core.config.Identity;
import de.qabel.core.crypto.QblECPublicKey;
import de.qabel.core.drop.DropMessage;
import de.qabel.desktop.cellValueFactory.BoxObjectCellValueFactory;
import de.qabel.desktop.config.ClientConfiguration;
import de.qabel.desktop.config.factory.BoxVolumeFactory;
import de.qabel.desktop.daemon.drop.ShareNotificationMessage;
import de.qabel.desktop.daemon.management.*;
import de.qabel.desktop.exceptions.QblStorageException;
import de.qabel.desktop.repository.DropMessageRepository;
import de.qabel.desktop.repository.exception.PersistenceException;
import de.qabel.desktop.storage.*;
import de.qabel.desktop.storage.cache.CachedBoxNavigation;
import de.qabel.desktop.ui.AbstractController;
import de.qabel.desktop.ui.DetailsController;
import de.qabel.desktop.ui.DetailsView;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.spongycastle.util.encoders.Hex;

import javax.inject.Inject;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static de.qabel.desktop.daemon.management.Transaction.TYPE.CREATE;
import static de.qabel.desktop.daemon.management.Transaction.TYPE.DELETE;
import static javafx.scene.Cursor.HAND;


public class RemoteFSController extends AbstractController implements Initializable {
	final String ROOT_FOLDER_NAME = "/";
	public static final int OPTION_WIDTH = 16;
	private static Image uploadFileImage = new Image(RemoteFSController.class.getResourceAsStream("/icon/upload.png"), OPTION_WIDTH, OPTION_WIDTH, true, true);
	private static Image uploadFolderImage = new Image(RemoteFSController.class.getResourceAsStream("/icon/folder-upload.png"), OPTION_WIDTH, OPTION_WIDTH, true, true);
	private static Image downloadImage = new Image(RemoteFSController.class.getResourceAsStream("/icon/download.png"), OPTION_WIDTH, OPTION_WIDTH, true, true);
	private static Image addFolderImage = new Image(RemoteFSController.class.getResourceAsStream("/icon/add_folder.png"), OPTION_WIDTH, OPTION_WIDTH, true, true);
	private static Image deleteImage = new Image(RemoteFSController.class.getResourceAsStream("/icon/delete.png"), OPTION_WIDTH, OPTION_WIDTH, true, true);
	private static Image shareImage = new Image(RemoteFSController.class.getResourceAsStream("/icon/share.png"), OPTION_WIDTH, OPTION_WIDTH, true, true);

	private BoxVolume volume;
	ReadOnlyBoxNavigation nav;
	LazyBoxFolderTreeItem rootItem;
	ObjectProperty<TreeItem<BoxObject>> hoveredItem = new SimpleObjectProperty<>(null);
	ResourceBundle resourceBundle;

	@Inject
	ClientConfiguration clientConfiguration;

	@Inject
	BoxVolumeFactory boxVolumeFactory;

	@Inject
	TransferManager loadManager;

	@Inject
	DropMessageRepository dropMessageRepository;

	@FXML
	private TreeTableView<BoxObject> treeTable;
	@FXML
	private TreeTableColumn<BoxObject, String> nameColumn;
	@FXML
	private TreeTableColumn<BoxObject, String> sizeColumn;
	@FXML
	private TreeTableColumn<BoxObject, String> dateColumn;
	@FXML
	private TreeTableColumn<BoxObject, Node> optionsColumn;
	@FXML
	private StackPane stack;

	private DetailsController details;


	public void initialize(URL location, ResourceBundle resources) {
		Cursor oldCursor = stack.getCursor();
		stack.setCursor(Cursor.WAIT);
		this.resourceBundle = resources;
		createObserver();
		initTreeTableView();


		setCellValueFactories();
		nameColumn.prefWidthProperty().bind(
				treeTable.widthProperty()
						.subtract(sizeColumn.widthProperty())
						.subtract(dateColumn.widthProperty())
						.subtract(optionsColumn.widthProperty())
		);

		treeTable.getSelectionModel().clearSelection();
		treeTable.getSelectionModel().selectedItemProperty().addListener((o, x1, value) -> {
			if (value != null) {
				showDetails(value);
			} else {
				Platform.runLater(details::hide);
			}
		});

		DetailsView detailsView = new DetailsView();
		details = (DetailsController) detailsView.getPresenter();
		detailsView.getViewAsync(stack.getChildren()::add);
		stack.setCursor(oldCursor);
	}

	private void showDetails(TreeItem<BoxObject> value) {
		new RemoteFileDetailsView(getNavigation(value), value.getValue()).getViewAsync(details::show);
	}

	private BoxNavigation getNavigation(TreeItem<BoxObject> value) {
		if (value instanceof LazyBoxFolderTreeItem) {
			return (BoxNavigation) ((LazyBoxFolderTreeItem) value).getNavigation();
		} else {
			return (BoxNavigation) ((LazyBoxFolderTreeItem)value.getParent()).getNavigation();
		}
	}

	private void createObserver() {
		clientConfiguration.addObserver((o, arg) -> {
			if (!(arg instanceof Identity)) {
				return;
			}
			initTreeTableView();
		});
	}

	private void initTreeTableView() {
		try {
			nav = createSetup();
			StaticTreeItemContainer virtualRoot = new StaticTreeItemContainer(new FakeBoxObject("virtualRoot"), null);
			StaticTreeItemContainer shareRoot = new StaticTreeItemContainer(new FakeBoxObject("Shares"), new ImageView(shareImage));
			rootItem = new LazyBoxFolderTreeItem(new BoxFolder(volume.getRootRef(), ROOT_FOLDER_NAME, new byte[16]), nav);
			rootItem.setExpanded(true);

			virtualRoot.getChildren().add(shareRoot);
			virtualRoot.getChildren().add(rootItem);
			virtualRoot.setExpanded(true);

			treeTable.setShowRoot(false);
			treeTable.setRoot(virtualRoot);

			if (nav instanceof PathNavigation) {
				Thread poller = new Thread(() -> {
					try {
						while (!Thread.interrupted()) {
							try {
								((CachedBoxNavigation)nav).refresh();
							} catch (QblStorageException e) {
								e.printStackTrace();
							}
							Thread.sleep(TimeUnit.MINUTES.toMillis(1));
						}
					} catch (InterruptedException ignored) {
					}
				});
				poller.setDaemon(true);
				poller.start();
			}
		} catch (QblStorageException e) {
			alert("failed to load remotefs", e);
		}
	}

	private void setCellValueFactories() {
		nameColumn.setCellValueFactory(new BoxObjectCellValueFactory(BoxObjectCellValueFactory.NAME));
		sizeColumn.setCellValueFactory(new BoxObjectCellValueFactory(BoxObjectCellValueFactory.SIZE));
		dateColumn.setCellValueFactory(new BoxObjectCellValueFactory(BoxObjectCellValueFactory.MTIME));

		treeTable.setRowFactory(param1 -> {
			TreeTableRow<BoxObject> row = new TreeTableRow<>();
			row.hoverProperty().addListener((observable, oldValue, newValue) -> {
				if (newValue) {
					hoveredItem.set(row.getTreeItem());
				} else if (oldValue && hoveredItem.get() == row.getTreeItem()) {
					hoveredItem.set(null);
				}
			});

			row.treeItemProperty().addListener((observable, oldValue, newValue) -> {
				ObservableList<String> styleClass = row.getStyleClass();
				if (newValue == rootItem) {
					styleClass.remove("child");
					styleClass.add("root");
				} else {
					styleClass.remove("root");
					styleClass.add("child");
				}
			});
			return row;
		});
		optionsColumn.setCellValueFactory(param -> {
			TreeItem<BoxObject> item = param.getValue();
			HBox bar = new HBox(3);
			SimpleObjectProperty<Node> result = new SimpleObjectProperty<>(bar);
			if (!(item.getValue() instanceof BoxFolder) && !(item.getValue() instanceof BoxFile)) {
				return result;
			}


			buttonFromImage(item, bar, downloadImage, this::download, "download");

			if (item.getValue() instanceof BoxFolder) {
				buttonFromImage(item, bar, uploadFileImage, this::uploadFile, "upload_file");
				buttonFromImage(item, bar, uploadFolderImage, this::uploadFolder, "upload_folder");
				buttonFromImage(item, bar, addFolderImage, this::createFolder, "create_folder");
			} else {
				spacer(bar);
				spacer(bar);
				spacer(bar);
			}

			buttonFromImage(item, bar, deleteImage, this::deleteItem, "delete");
			if (item.getValue() instanceof BoxFolder) {
				spacer(bar);
			} else {
				buttonFromImage(item, bar, shareImage, this::share, "share");
			}

			return result;
		});
	}

	void share(TreeItem<BoxObject> item) {
		if (!(item.getValue() instanceof BoxFile)) {
			return;
		}

		if (!(item.getParent() instanceof LazyBoxFolderTreeItem)) {
			return;
		}
		LazyBoxFolderTreeItem folder = (LazyBoxFolderTreeItem) item.getParent();
		if (!(folder.getNavigation() instanceof BoxNavigation)) {
			return;
		}

		try {
			Identity sender = clientConfiguration.getSelectedIdentity();
			Contact receiver = new Contact("me", sender.getDropUrls(), sender.getEcPublicKey());
			BoxFile objectToShare = (BoxFile) item.getValue();
			String message = "Hey, I got a share for you!";
			BoxNavigation navigation = (BoxNavigation) folder.getNavigation();

			shareAndSendMessage(sender, receiver, objectToShare, message, navigation);
		} catch (Exception e) {
			alert(e);
		}
	}

	private void shareAndSendMessage(Identity sender, Contact receiver, BoxFile objectToShare, String message, BoxNavigation navigation) throws QblStorageException, PersistenceException {
		QblECPublicKey owner = sender.getEcPublicKey();
		BoxExternalReference ref = navigation.createFileMetadata(owner, objectToShare);
		ShareNotificationMessage share = new ShareNotificationMessage(ref.url, Hex.toHexString(ref.key), message);
		System.out.println(share.toJson());
		dropMessageRepository.addMessage(
				new DropMessage(sender, share.toJson(), DropMessageRepository.PAYLOAD_TYPE_SHARE_NOTIFICATION),
				sender,
				receiver,
				false
		);
	}

	private void spacer(HBox bar) {
		Label label = new Label();
		label.setPrefWidth(OPTION_WIDTH);
		bar.getChildren().add(label);
	}

	private void buttonFromImage(TreeItem<BoxObject> item, HBox bar, Image image, Consumer<TreeItem<BoxObject>> handler, String name) {
		ImageView buttonIcon = new ImageView(image);
		Pane button = new Pane(buttonIcon);
		button.getStyleClass().add("inline-button");
		button.setCursor(HAND);
		button.setOnMouseClicked(event -> handler.accept(item));
		button.visibleProperty().bind(hoveredItem.isEqualTo(item));
		button.setId(name + "_" + treeTable.getRow(item));
		Tooltip tooltip = new Tooltip(resourceBundle.getString("option_" + name + "_tooltip"));
		Tooltip.install(button, tooltip);
		bar.getChildren().add(button);
	}

	private ReadOnlyBoxNavigation createSetup() throws QblStorageException {
		volume = boxVolumeFactory.getVolume(clientConfiguration.getAccount(), clientConfiguration.getSelectedIdentity());
		nav = volume.navigate();

		return nav;
	}

	private void uploadFile(TreeItem<BoxObject> item) {
		if (!(item instanceof LazyBoxFolderTreeItem)) {
			return;
		}

		FileChooser chooser = new FileChooser();
		String title = resourceBundle.getString("chooseFile");
		chooser.setTitle(title);
		List<File> list = chooser.showOpenMultipleDialog(treeTable.getScene().getWindow());
		for (File file : list) {
			Path destination = ((LazyBoxFolderTreeItem) item).getPath().resolve(file.getName());
			Path source = file.toPath();
			upload(source, destination);
		}
	}

	void upload(Path source, Path destination) {
		Upload upload = new ManualUpload(CREATE, volume, source, destination);
		loadManager.addUpload(upload);
	}

	private void uploadFolder(TreeItem<BoxObject> item) {
		if (item == null || !(item.getValue() instanceof BoxFolder) || !(item instanceof LazyBoxFolderTreeItem)) {
			return;
		}

		DirectoryChooser chooser = new DirectoryChooser();
		String title = resourceBundle.getString("chooseFolder");
		chooser.setTitle(title);
		File directory = chooser.showDialog(treeTable.getScene().getWindow());
		try {
			chooseUploadDirectory(directory, item);
		} catch (IOException e) {
			alert("failed to upload folder", e);
		}
	}

	private void download(TreeItem<BoxObject> item) {
		try {
			DirectoryChooser chooser = new DirectoryChooser();
			chooser.setTitle(resourceBundle.getString("downloadFolder"));
			File directory = chooser.showDialog(treeTable.getScene().getWindow());
			BoxObject boxObject = item.getValue();

			Path path;
			LazyBoxFolderTreeItem folderTreeItem;
			if (item instanceof LazyBoxFolderTreeItem) {
				folderTreeItem = (LazyBoxFolderTreeItem) item;
				path = folderTreeItem.getPath();
			} else {
				folderTreeItem = (LazyBoxFolderTreeItem) item.getParent();
				path = folderTreeItem.getPath().resolve(boxObject.name);
			}
			ReadOnlyBoxNavigation navigation = folderTreeItem.getNavigation();

			downloadBoxObject(boxObject, navigation, path, directory.toPath());
		} catch (QblStorageException e) {
			alert(e);
		}
	}

	private void createFolder(TreeItem<BoxObject> item) {
		if (item == null || !(item instanceof LazyBoxFolderTreeItem)) {
			return;
		}

		TextInputDialog dialog = new TextInputDialog(resourceBundle.getString("name"));
		dialog.setHeaderText(null);
		dialog.setTitle(resourceBundle.getString("createFolder"));
		dialog.setContentText(resourceBundle.getString("folderName"));
		Optional<String> result = dialog.showAndWait();
		new Thread(() -> {
			result.ifPresent(name -> {
				LazyBoxFolderTreeItem lazyItem = (LazyBoxFolderTreeItem) item;

				try {
					createFolder(lazyItem.getPath().resolve(name));
				} catch (QblStorageException e) {
					e.printStackTrace();
				}
			});
		}).start();
	}

	private void deleteItem(TreeItem<BoxObject> item) {
		try {
			if (item.getParent() != null) {

				Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
				alert.setTitle(resourceBundle.getString("deleteQuestion"));
				alert.setHeaderText(resourceBundle.getString("deleteFolder") + item.getValue().name + " ?");
				Optional<ButtonType> result = alert.showAndWait();

				LazyBoxFolderTreeItem updateTreeItem = (LazyBoxFolderTreeItem) item.getParent();
				Path path = updateTreeItem.getPath().resolve(item.getValue().name);

				deleteBoxObject(result.get(), path, item.getValue());
			}
		} catch (QblStorageException e) {
			alert(e);
		}
	}


	void chooseUploadDirectory(File directory, TreeItem<BoxObject> item) throws IOException {
		Path destination = Paths.get(((LazyBoxFolderTreeItem) item).getPath().toString(), directory.getName());
		uploadDirectory(directory.toPath(), destination);
	}

	void uploadDirectory(Path source, Path destination) throws IOException {

		Files.walkFileTree(source, new FileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				loadManager.addUpload(new ManualUpload(CREATE, volume, dir, resolveDestination(dir), true));
				return FileVisitResult.CONTINUE;
			}

			private Path resolveDestination(Path dir) {
				return destination.resolve(source.relativize(dir));
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				loadManager.addUpload(new ManualUpload(CREATE, volume, file, resolveDestination(file), false));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.TERMINATE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}
		});
	}


	void createFolder(Path destination) throws QblStorageException {
		loadManager.addUpload(new ManualUpload(CREATE, volume, null, destination, true));
	}

	void deleteBoxObject(ButtonType confim, Path path, BoxObject object) throws QblStorageException {
		if (confim != ButtonType.OK) {
			return;
		}

		loadManager.addUpload(new ManualUpload(DELETE, volume, null, path, object instanceof BoxFolder));
	}

	void downloadBoxObject(BoxObject boxObject, ReadOnlyBoxNavigation nav, Path source, Path destination) throws QblStorageException {
		destination = destination.resolve(boxObject.name);
		if (boxObject instanceof BoxFile) {
			downloadFile((BoxFile)boxObject, nav, source, destination);
		} else {
			downloadBoxFolder(nav, source, destination);
		}
	}

	private void downloadBoxFolder(ReadOnlyBoxNavigation nav, Path source, Path destination) throws QblStorageException {
		loadManager.addDownload(new ManualDownload(CREATE, volume, source, destination, true));

		for (BoxFile file : nav.listFiles()) {
			downloadFile(file, nav, source.resolve(file.name), destination.resolve(file.name));
		}
		for (BoxFolder folder : nav.listFolders()) {
			downloadBoxFolder(nav.navigate(folder), source.resolve(folder.name), destination.resolve(folder.name));
		}
	}

	private void downloadFile(BoxFile file, ReadOnlyBoxNavigation nav, Path source, Path destination) {
		loadManager.addDownload(new ManualDownload(file.mtime, CREATE, volume, source, destination, false));
	}

	private ReadOnlyBoxNavigation getNavigator(BoxFolder folder) throws QblStorageException {
		ReadOnlyBoxNavigation newNav = nav;

		if (!(folder == null) && !folder.name.equals(ROOT_FOLDER_NAME)) {
			newNav = nav.navigate(folder);
		}

		return newNav;
	}

	ResourceBundle getRessource(){
		return resourceBundle;
	}
}
