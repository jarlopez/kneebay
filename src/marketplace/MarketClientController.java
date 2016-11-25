package marketplace;

import bank.RejectedException;
import common.Item;
import common.ItemWish;
import common.rmi.interfaces.Account;
import common.rmi.interfaces.Bank;
import common.rmi.interfaces.Marketplace;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import marketplace.gui.models.ItemModel;
import marketplace.rmi.MarketClientImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class MarketClientController implements Initializable {
    private static final Logger log = Logger.getLogger(MarketClientController.class.getName());

    @FXML public ToggleButton registerButton;
    @FXML public TextField usernameField;
    @FXML public Label fundsLabel;
    @FXML public Label marketplaceLabel;
    @FXML public TextArea logArea;
    @FXML public TableView marketplaceTable;
    @FXML public ListView wishList;

    public static final int INITIAL_FUNDS = 1000;

    private MarketClientImpl client;

    private Account account;
    private Bank bank;
    private Marketplace marketplace;

    private String marketplaceName = Marketplace.DEFAULT_MARKETPLACE;
    private String bankName = Bank.DEFAULT_BANK;
    private String username;

    private static final String DISCONNECT_STR = "Disconnect";
    private static final String REGISTER_STR = "\u2007Register\u2007";

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        registerButton.setText(REGISTER_STR);
        initializeMarketplaceTable();
    }

    private void initializeMarketplaceTable() {
        TableColumn<ItemModel, String> priceCol = new TableColumn<>("Price");
        priceCol.setMinWidth(75);
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<ItemModel, String> titleCol = new TableColumn<>("Title");
        titleCol.setMinWidth(100);
        titleCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<ItemModel, String> catCol = new TableColumn<>("Category");
        catCol.setMinWidth(125);
        catCol.setCellValueFactory(new PropertyValueFactory<>("category"));


        TableColumn actionCol = new TableColumn("Actions");
        catCol.setMinWidth(100);
        Callback<TableColumn<ItemModel, String>, TableCell<ItemModel, String>> cellFactory =
                new Callback<TableColumn<ItemModel, String>, TableCell<ItemModel, String>>() {
                    @Override
                    public TableCell call(final TableColumn<ItemModel, String> param) {
                        final TableCell<ItemModel, String> cell = new TableCell<ItemModel, String>() {

                            final Button buyButton = new Button("Buy");
                            final Button removeButton = new Button("Remove");

                            @Override
                            public void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty) {
                                    setGraphic(null);
                                    setText(null);
                                } else {
                                    final ItemModel itemModel = getTableView().getItems().get(getIndex());
                                    if (itemModel.getSeller().equals(username)) {
                                        removeButton.setOnAction((ActionEvent event) -> handleRemoveItem(itemModel));
                                        setGraphic(removeButton);
                                        setText(null);
                                    } else {
                                        buyButton.setOnAction((ActionEvent event) -> handleBuyItem(itemModel));
                                        setGraphic(buyButton);
                                        setText(null);
                                    }
                                }
                            }
                        };
                        return cell;
                    }
                };
        actionCol.setCellFactory(cellFactory);

        marketplaceTable.getColumns().addAll(priceCol, titleCol, catCol, actionCol);
    }



    private void handleBuyItem(ItemModel itemModel) {
        log.info("Buying item " + itemModel.getName() + " for " + username);
        try {
            Item clone = itemModel.generateItem();
            marketplace.buyItem(clone, username);
        } catch (RemoteException e) {
            e.printStackTrace();
            log.severe("Could not purchase item " + itemModel.getName());
            logArea.appendText("Could not purchase the item " + itemModel.getName() + "\n");
        }
    }

    private void handleRemoveItem(ItemModel itemModel) {
        try {
            Item clone = itemModel.generateItem();
            marketplace.removeItem(clone, username);
        } catch (RemoteException e) {
            e.printStackTrace();
            logArea.appendText("Could not remove the item " + itemModel.getName() + "\n");
        }
    }

    public void onRegisterUser(ActionEvent actionEvent) {
        String username = usernameField.getText();

        log.info("Handling user registration for " + username);
        try {
            client = new MarketClientImpl(username, this);
            this.username = username;
            bank = (Bank) Naming.lookup(bankName);
            try {
                log.info("Trying to create account");
                account = bank.newAccount(username);
                account.deposit(INITIAL_FUNDS);
            } catch (RejectedException rEx) {
                log.info("Getting account");
                account = bank.getAccount(username);
            }
            marketplace = (Marketplace) Naming.lookup(marketplaceName);
            marketplace.register(username, null, account, client);


            updateAvailableFunds();
            usernameField.setEditable(false);
            registerButton.setText(DISCONNECT_STR);
            marketplaceLabel.setText("Marketplace: " + marketplaceName);

            // TODO Handle accordingly
        } catch (RemoteException | MalformedURLException | NotBoundException e) {
            e.printStackTrace();
            registerButton.setText(REGISTER_STR);
            registerButton.setSelected(false);
        }
    }

    public void onUnregisterUser(ActionEvent actionEvent) {
        registerButton.setText(REGISTER_STR);
        usernameField.setEditable(true);
        try {
            marketplace.unregister(username);
        } catch (RemoteException e) {
            // TODO Show exception
            e.printStackTrace();
            logArea.appendText("ERR: Could not unregister from marketplace\n");
        }
    }

    public void onNewWish(ActionEvent actionEvent) throws IOException {
        Stage stage = new Stage();
        Parent root = FXMLLoader.load(getClass().getResource("/add-wish-modal.fxml"));
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Add New Wish to " + marketplaceName);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(((Node) actionEvent.getSource()).getScene().getWindow());

        TextField newWishPrice = (TextField) scene.lookup("#newWishPrice");
        ChoiceBox categoryChoice = (ChoiceBox) scene.lookup("#newWishCategory");
        Button submitButton = (Button) scene.lookup("#submitNewItem");

        categoryChoice.getItems().addAll(Item.Category.values());

        submitButton.setOnAction(event -> {
            float price = Float.parseFloat(newWishPrice.getText());
            Item.Category cat = (Item.Category) categoryChoice.getValue();
            ItemWish wish = new ItemWish(cat, price);
            try {
                marketplace.addWish(wish, username);
                wishList.getItems().add(wish.displayString());
            } catch (RemoteException e) {
                e.printStackTrace();
                logArea.appendText("Could not create the new wish.\n");
            }
            stage.close();
        });

        stage.show();
    }

    public void onNewItem(ActionEvent actionEvent) throws IOException {
        Stage stage = new Stage();
        Parent root = FXMLLoader.load(getClass().getResource("/add-item-modal.fxml"));
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Add New Item to " + marketplaceName);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(((Node) actionEvent.getSource()).getScene().getWindow());

        TextField newItemTitle = (TextField) scene.lookup("#newItemTitle");
        TextField newItemPrice = (TextField) scene.lookup("#newItemPrice");
        ChoiceBox categoryChoice = (ChoiceBox) scene.lookup("#newItemCategory");
        Button submitButton = (Button) scene.lookup("#submitNewItem");

        categoryChoice.getItems().addAll(Item.Category.values());

        submitButton.setOnAction(event -> {
            // TODO Validate
            Item newItem = Item.builder()
                    .name(newItemTitle.getText())
                    .category((Item.Category) categoryChoice.getValue())
                    .price(Float.parseFloat(newItemPrice.getText()))
                    .seller(username)
                    .build();
            try {
                marketplace.addItem(newItem);
            } catch (RemoteException e) {
                e.printStackTrace();
                logArea.appendText("ERR: Could not create the new item '" + newItem.getName() + "'\n");
            }
            stage.close();
        });

        stage.show();

    }

    public void onToggleRegistration(ActionEvent actionEvent) {
        if (registerButton.isSelected()) {
            onRegisterUser(null);
        } else {
            onUnregisterUser(null);
        }
    }

    private ObservableList<ItemModel> generateItemModels(Collection<Item> items) {
        ObservableList<ItemModel> data = FXCollections.observableArrayList();
        for (Item it : items) {
            data.add(new ItemModel(it.getId(), it.getName(), it.getPrice(), it.getCategory(), it.getSeller()));
        }
        return data;
    }

    public void onWishNotify(Item type) {
        logArea.appendText("An item from your wish list is available!\n");
    }

    public void onItemSold(Item item) {
        log.info("Sold item " + item.getName());
        logArea.appendText("Your item '" + item.getName() + "' sold for " + item.getPrice() + "\n");
        updateAvailableFunds();
    }

    private void updateAvailableFunds() {
        Platform.runLater(() -> {
            try {
                fundsLabel.setText("$" + String.valueOf(bank.getAccount(username).getBalance()));
            } catch (RemoteException e) {
                e.printStackTrace();
                logArea.appendText("ERR: Could not get balance from bank\n");
            }
        });
    }

    public void onItemPurchased(Item item) {
        logArea.appendText("Purchased item '" + item.getName() + "' for " + item.getPrice() + "\n");
        updateAvailableFunds();


        Platform.runLater(() -> wishList.getItems().removeIf(o -> {
            String str = o.toString();
            float max = Float.valueOf(str.substring(str.indexOf("(max: ")+5,str.indexOf(")")));
            return str.startsWith(item.getCategory().name()) && max >= item.getPrice();
        }));
    }

    public void onLackOfFunds() {
        log.info("Lack of funds! Cannot purchase");
        logArea.appendText("Lack of funds! Cannot purchase\n");
    }

    public void updateMarketplace(Collection<Item> allItems) {
        ObservableList<ItemModel> data = generateItemModels(allItems);
        marketplaceTable.setItems(data);
    }

    public void onException(String data) {

    }
}
