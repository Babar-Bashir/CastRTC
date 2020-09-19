package com.avantica.videochat.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.avantica.videochat.model.Call;
import com.avantica.videochat.model.CallType;
import com.avantica.videochat.model.User;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainViewModel extends ViewModel {

    //region Private constants
    private final static String USERNAME = "username";
    private final static String PLATFORM = "platform";
    private final static String USERS = "users";
    private final static String ORIGIN = "origin";
    private final static String DESTINATION = "destination";
    private final static String CALLS = "calls";
    private final static String ANDROID = "Android";
    //endregion

    //region Private properties
    private FirebaseFirestore db;
    private String uuid = "";
    private User currentUser;

    private Boolean isOriginChecked = false;
    private Boolean isDestinationChecked = false;
    private Boolean isOriginBusy = false;
    private Boolean isDestinationBusy = false;
    //endregion

    //region Public properties
    public MutableLiveData<String> onUsername = new MutableLiveData<>();
    public MutableLiveData<Boolean> onNoUsername = new MutableLiveData();
    public MutableLiveData<List<User>> onUsersUpdated = new MutableLiveData<>();
    public MutableLiveData<Call> onCallUpdated = new MutableLiveData<>();
    public MutableLiveData<Boolean> onUserBusy = new MutableLiveData<>();

    public List<User> users = new ArrayList<User>();
    //endregion

    //region Public API
    public MainViewModel(Context context){
        db = FirebaseFirestore.getInstance();
        checkUsername(context);
    }

    public void setUsername(String username) {
        onUsername.setValue(username);

        Map<String, Object> usernameDict = new HashMap<>();
        usernameDict.put(USERNAME, username);
        usernameDict.put(PLATFORM, ANDROID);

        db.collection(USERS)
                .document(uuid)
                .set(usernameDict, SetOptions.merge());
        
        getUsers();
        getCalls();
    }


    public void callUser(User user) {

        /*
         * Prevent the user to call a user who is in the middle of a call.
         * Firebase doesn't support OR queries, so the way to perform one is to do two different queries
         * and check both results.
         */
        isDestinationChecked = false;
        isOriginChecked = false;
        isOriginBusy = false;
        isDestinationBusy = false;

        db.collection(CALLS)
                .whereEqualTo(ORIGIN, user.id)
                .get()
                .addOnCompleteListener(task -> {
                    isOriginChecked = true;
                    isOriginBusy = !task.getResult().isEmpty();
                    checkIsBusy(user);
                });

        db.collection(CALLS)
                .whereEqualTo(DESTINATION, user.id)
                .get()
                .addOnCompleteListener(task -> {
                    isDestinationChecked = true;
                    isDestinationBusy = !task.getResult().isEmpty();
                    checkIsBusy(user);
                });
    }

    //endregion

    //region Private helpers

    private void checkUsername(Context context) {

        @SuppressLint("HardwareIds")
        String android_id = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);

            this.uuid = android_id;
            db.collection(USERS).document(uuid).get().addOnCompleteListener(task -> {
                if (task.getResult().exists()) {
                    User user = new User(task.getResult());
                    String username = user.username;
                    onUsername.setValue(username);
                    currentUser = user;
                    getUsers();
                    getCalls();
                } else {
                    onNoUsername.setValue(false);
                }
            });
    }

    private void getUsers() {
        db.collection(USERS).addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                return;
            }
            if (queryDocumentSnapshots != null) {
                users.clear();
                for(DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                    User user = new User(document);
                    if (!user.id.equalsIgnoreCase(uuid)) {
                        users.add(user);
                    }
                }

                onUsersUpdated.setValue(users);
            }
        });
    }

    private void getCalls(){
        db.collection(CALLS).whereEqualTo(DESTINATION, uuid).addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                return;
            } else if (queryDocumentSnapshots != null) {
                for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {

                        Map<String, Object> data = document.getData();
                        String callerId = (String) data.get(ORIGIN);
                        User caller = null;
                        for (User user : users) {
                            if(user.id.equalsIgnoreCase(callerId)) {
                                caller = user;
                                break;
                            }
                        }
                        if (caller != null) {
                            onCallUpdated.setValue(new Call(document.getId(), callerId, CallType.INCOMING, caller, currentUser));
                        }
                }
            }
        });
    }

    private void checkIsBusy(User user) {
        if (isDestinationChecked && isOriginChecked) {
            if (isOriginBusy || isDestinationBusy) {
                onUserBusy.setValue(true);
            } else {
                Map<String, Object> callInfo = new HashMap<>();
                callInfo.put(ORIGIN, uuid);
                callInfo.put(DESTINATION, user.id);
                DocumentReference doc = db.collection(CALLS).document();
                doc.set(callInfo);
                onCallUpdated.setValue(new Call(doc.getId(), uuid, CallType.OUTGOING, currentUser, user));
            }
        }
    }
    //endregion
}
