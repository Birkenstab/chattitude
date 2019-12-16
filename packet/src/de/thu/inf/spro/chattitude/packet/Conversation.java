package de.thu.inf.spro.chattitude.packet;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Conversation {

    private static final String FIELD_ID = "id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_USERS = "users";

    private int id;
    private String name;
    private Message message;
    private User[] users;

    public Conversation(JsonObject json){
        id = json.get(FIELD_ID).asInt();
        name = json.get(FIELD_NAME).asString();

        if(json.get(FIELD_MESSAGE) != null) message = new Message(json.get(FIELD_MESSAGE).asObject());
        else message = null;

        List<User> userList = new ArrayList<>();
        json.get(FIELD_USERS).asArray().iterator().forEachRemaining(v -> userList.add(new User(v.asObject())));
        users = new User[userList.size()];
        users = userList.toArray(users);
    }

    public Conversation(User[] users){
        this.id = -1;
        this.name = "";
        this.message = null;
        this.users = users;
    }

    public Conversation(int id, String name, Message message){
        this.id = id;
        this.name = name;
        this.message = message;
        this.users = new User[]{};
    }

    public int getId(){
        return id;
    }

    public String getName(){
        return name;
    }

    public Message getMessage(){
        return message;
    }

    public User[] getUsers(){
        return users;
    }

    public void setId(int id){
        this.id = id;
    }

    public void setName(String name){
        this.name = name;
    }

    public void setUsers(List<User> userList){
        users = new User[userList.size()];
        users = userList.toArray(users);
    }
    public void setMessage(Message message) {
        this.message = message;
    }

    public JsonObject asJson(){
        JsonObject json = new JsonObject();

        json.add(FIELD_ID, id);
        json.add(FIELD_NAME, name);
        if(message != null) json.add(FIELD_MESSAGE, message.asJson());
        JsonArray usersArray = new JsonArray();
        Arrays.stream(users).map(User::asJson).forEach(usersArray::add);
        json.add(FIELD_USERS, usersArray);

        return json;
    }

}