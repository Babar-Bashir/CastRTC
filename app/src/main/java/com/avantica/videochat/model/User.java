package com.avantica.videochat.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.firebase.firestore.DocumentSnapshot;

public class User implements Parcelable {

    public String username;
    public String platform;
    public String id;

    public User(DocumentSnapshot documentSnapshot) {
        id = documentSnapshot.getId();
        username = documentSnapshot.getString("username");
        platform = documentSnapshot.getString("platform");
    }


    protected User(Parcel in) {
        username = in.readString();
        platform = in.readString();
        id = in.readString();
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(username);
        dest.writeString(platform);
        dest.writeString(id);
    }
}
