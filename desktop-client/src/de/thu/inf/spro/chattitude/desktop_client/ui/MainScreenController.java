package de.thu.inf.spro.chattitude.desktop_client.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import de.thu.inf.spro.chattitude.desktop_client.Client;
import de.thu.inf.spro.chattitude.desktop_client.DownloadManager;
import de.thu.inf.spro.chattitude.desktop_client.message.ChatMessage;
import de.thu.inf.spro.chattitude.desktop_client.message.TextMessage;
import de.thu.inf.spro.chattitude.desktop_client.ui.cell.ChatMessageCell;
import de.thu.inf.spro.chattitude.desktop_client.ui.cell.ConversationCell;
import de.thu.inf.spro.chattitude.desktop_client.ui.controller.TextMessageController;
import de.thu.inf.spro.chattitude.packet.Conversation;
import de.thu.inf.spro.chattitude.packet.Message;
import de.thu.inf.spro.chattitude.packet.User;
import de.thu.inf.spro.chattitude.packet.packets.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.skin.ListViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;

public class MainScreenController implements Initializable {

    @FXML
    private JFXTextField messageField;
    @FXML
    private JFXListView<Conversation> conversationsList;
    @FXML
    private JFXListView<ChatMessage> messageHistoryList;
    @FXML
    private StackPane stackPane;
    @FXML
    private JFXButton editConversationButton;

    private Client client;
    private DownloadManager downloadManager;
    private Conversation selectedConversation;
    private ObservableList<Conversation> conversations; // TODO automaatisch sortieren nach Datum
    private ObservableList<ChatMessage> messagesOfSelectedConversation;
    private boolean allMessagesOfCurrentConversationLoaded = false;
    private boolean loadingHistory = false;

    public MainScreenController() {
        System.out.println("LoginScreenController");
        client = App.getClient();
        downloadManager = new DownloadManager(client);
        messagesOfSelectedConversation = FXCollections.observableArrayList();
        conversations = FXCollections.observableArrayList();

        client.setOnMessage(message -> Platform.runLater(() -> {
            int conversationId = message.asMessage().getConversationId();
            if (selectedConversation != null && conversationId == selectedConversation.getId()) {
                messagesOfSelectedConversation.add(message);
            }
            Conversation conversation = getConversation(conversationId);
            if (conversation == null) {
                System.out.println("Warning: Received message for unknown conversation " + conversationId);
                return;
            }
            conversation.setMessage(message.asMessage());
            replaceConversation(conversation, conversation); // Update triggern

        }));

        client.setOnConversationUpdated(newConversation -> Platform.runLater(() -> {
            Conversation oldConversation = getConversation(newConversation.getId());
            if (oldConversation == null) { // new conversation
                conversations.add(newConversation);
            } else {
                replaceConversation(oldConversation, newConversation);
            }
        }));

        client.setOnMessageHistory(packet -> Platform.runLater(() -> {
            loadingHistory = false;
            if (selectedConversation.getId() != packet.getConversationId())
                return;

            if (packet.getLastMessageId() != messagesOfSelectedConversation.get(0).asMessage().getId()) {
                System.out.println("Warning hä das wollt ich doch gar nicht");
                return;
            }

            if (packet.getMessages().length == 0) {
                allMessagesOfCurrentConversationLoaded = true;
                return;
            }

            ListViewSkin<?> ts = (ListViewSkin<?>) messageHistoryList.getSkin();
            VirtualFlow<?> vf = (VirtualFlow<?>) ts.getChildren().get(0);
            int index = vf.getFirstVisibleCell().getIndex() + 1;
            if (index >= messagesOfSelectedConversation.size())
                index--;
            ChatMessage topMost = messagesOfSelectedConversation.get(index);

            for (Message rawMessage : packet.getMessages()) {
                ChatMessage message = ChatMessage.of(rawMessage);
                if (!messagesOfSelectedConversation.contains(message)) {
                    messagesOfSelectedConversation.add(0, message);
                }
            }
            messageHistoryList.scrollTo(topMost);
            checkToLoadHistory();
        }));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client.setOnGetAttachment(downloadManager);
        client.setOnConversations(newConversations -> Platform.runLater(() -> {
            conversations.clear();
            conversations.addAll(newConversations);
        }));
        client.send(new GetConversationsPacket());

        conversationsList.setCellFactory(param -> {
            var cell = new ConversationCell();
            cell.setOnMouseClicked(event -> messageField.requestFocus());
            return cell;
        });
        conversationsList.setItems(conversations);
        conversationsList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newSelectedConversation) -> {
            selectedConversation = newSelectedConversation;
            messagesOfSelectedConversation.clear();
            allMessagesOfCurrentConversationLoaded = false;
            loadingHistory = false;
            messageField.setText("");

            editConversationButton.setVisible(selectedConversation.isAdmin(client.getCredentials().getUserId()));

            if (selectedConversation != null && selectedConversation.getMessage() != null) {
                Message rawMessage = selectedConversation.getMessage();
                messagesOfSelectedConversation.add(ChatMessage.of(rawMessage));

                ListViewSkin<?> ts = (ListViewSkin<?>) messageHistoryList.getSkin();
                VirtualFlow<?> vf = (VirtualFlow<?>) ts.getChildren().get(0);
                vf.positionProperty().addListener((observable2, oldValue2, newValue) -> checkToLoadHistory());

                loadMoreMessages();
            } else {
                allMessagesOfCurrentConversationLoaded = true;
            }
        });

        messageHistoryList.setCellFactory(param -> new ChatMessageCell(downloadManager));
        messageHistoryList.setItems(messagesOfSelectedConversation);
    }

    @FXML
    private void messageFieldKeyPress(KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.ENTER)  {
            sendMessage();
        }
    }

    @FXML
    private void editConversation() {
        EditConversationPopUp popUp = new EditConversationPopUp(client, selectedConversation);
        stackPane.getChildren().add(popUp);
    }

    private void loadMoreMessages() {
        System.out.println("Load more messages for " + selectedConversation);
        if (selectedConversation.getMessage() == null)
            return;

        loadingHistory = true;
        int lastMessageId = messagesOfSelectedConversation.get(0).asMessage().getId();

        client.send(new MessageHistoryPacket(selectedConversation.getId(), lastMessageId));
    }

    private void checkToLoadHistory() {
        ListViewSkin<?> ts = (ListViewSkin<?>) messageHistoryList.getSkin();
        VirtualFlow<?> vf = (VirtualFlow<?>) ts.getChildren().get(0);
        var firstVisible = vf.getFirstVisibleCell();
        if (firstVisible == null)
            return;
        int first = firstVisible.getIndex();
        if (first == 0) {
            if (!allMessagesOfCurrentConversationLoaded && !loadingHistory) {
                loadMoreMessages();
            }
        }
    }

    public void newChat() {
        // TODO

        String groupName = "Group " + new SimpleDateFormat("HH:mm:ss").format(new Date());
        User[] userArray = new User[]{new User(1,"testUser1"), new User(2, "testUser2")};

        Conversation dummyConversation = new Conversation(userArray[1]);
        CreateConversationPacket packet = new CreateConversationPacket(dummyConversation);
        client.send(packet);
        client.setOnConversationCreated(conversation -> Platform.runLater(() -> {
            conversations.add(conversation);
            conversationsList.getSelectionModel().select(conversation);
        }));

    }
    public void startUserChat() {
        CreateSingleChatPopUp popUp = new CreateSingleChatPopUp(client);
        // TODO - JAN
        stackPane.getChildren().add(popUp);
    }

    public void startGroupChat() {
        // TODO - JAN
        CreateGroupChatPopUp popUp = new CreateGroupChatPopUp(client);
        stackPane.getChildren().add(popUp);
    }



    public void sendMessage() {
        if (messageField.getText().equals(""))
            return;
        System.out.println("Send");
        TextMessage message = new TextMessage(selectedConversation.getId(), messageField.getText());
        client.send(new MessagePacket(message.asMessage()));

        messageField.setText("");
    }

    private Conversation getConversation(int id) {
        for (Conversation conversation : conversations) {
            if (conversation.getId() == id)
                return conversation;
        }
        return null;
    }

    private void replaceConversation(Conversation oldConversation, Conversation newConversation) {
        int index = conversations.indexOf(oldConversation);
        conversations.set(index, newConversation); // Replace with new conversation object
    }

}
