package com.avantica.videochat.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Call implements Parcelable {

    public String id;
    public String roomId;
    public CallType callType;
    public User origin;
    public User destination;

    public Call(String id, String roomId, CallType callType, User origin, User destination) {
        this.id = id;
        this.roomId = roomId;
        this.callType = callType;
        this.origin = origin;
        this.destination = destination;
    }

    protected Call(Parcel in) {
        id = in.readString();
        roomId = in.readString();
        if (in.readInt() == CallType.INCOMING.ordinal()) {
            callType = CallType.INCOMING;
        } else {
            callType = CallType.OUTGOING;
        }
        origin = (User) in.readParcelable(User.class.getClassLoader());
        destination = (User) in.readParcelable(User.class.getClassLoader());
    }

    public static final Creator<Call> CREATOR = new Creator<Call>() {
        @Override
        public Call createFromParcel(Parcel in) {
            return new Call(in);
        }

        @Override
        public Call[] newArray(int size) {
            return new Call[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(roomId);
        dest.writeInt(callType.ordinal());
        dest.writeParcelable(origin, flags);
        dest.writeParcelable(destination, flags);
    }

    public String getCurrentUserId() {
        if (callType == CallType.OUTGOING) {
            return origin.id;
        } else {
            return destination.id;
        }
    }
}
