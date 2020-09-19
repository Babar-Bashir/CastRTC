package com.avantica.videochat.call;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.avantica.videochat.model.Call;
import com.avantica.videochat.model.CallType;
import com.avantica.videochat.model.User;
import com.avantica.videochat.rtc.RTCClient;
import com.avantica.videochat.rtc.RTCClientInterface;
import com.avantica.videochat.rtc.RTCClientState;
import com.avantica.videochat.signaling.SignalingManager;
import com.avantica.videochat.signaling.SignalingManagerInterface;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.Map;

public class CallViewModel extends ViewModel implements SignalingManagerInterface, RTCClientInterface {

    //region Private constants
    private final static String CALLS = "calls";
    private final static String ORIGIN = "origin";
    //endregion

    //region Private properties
    private FirebaseFirestore db;
    private Call currentCall;
    private SignalingManager signalingManager;
    private RTCClient client;
    //endregion

    //region Public properties
    public MutableLiveData<VideoTrack> localVideoTrack = new MutableLiveData();
    public MutableLiveData<VideoTrack> remoteVideoTrack = new MutableLiveData();
    public MutableLiveData<RTCClientState> state = new MutableLiveData<>();
    //endregion

    //region Public API
    public CallViewModel(Context context, Call call, String userId){
        db = FirebaseFirestore.getInstance();
        currentCall = call;
        signalingManager = new SignalingManager(this, userId);
        client = new RTCClient(context,this);
        client.startConnection();
        getCalls(userId);

        if (call.callType == CallType.OUTGOING) {
            client.makeOffer();
        }
    }

    public void acceptCall() {
        signalingManager.joinRoom(currentCall.roomId);
    }

    public void rejectCall(){
        finishCall();
    }

    public void endCall() {
        finishCall();
    }
    //endregion

    private void finishCall() {
        db.collection(CALLS).document(currentCall.id).delete().addOnCompleteListener((result) -> {
            signalingManager.finishCall();
            client.disconnect();
        });

    }

    private void getCalls(String userId){
        db.collection(CALLS).whereEqualTo(ORIGIN, userId).addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                return;
            } else if (queryDocumentSnapshots != null) {
                for (DocumentChange change : queryDocumentSnapshots.getDocumentChanges()) {
                    if (change.getType() == DocumentChange.Type.REMOVED) {
                        finishCall();
                    }
                }
            }
        });
    }

    //region RTCClientInterface
    @Override
    public void rtcClientStartCallWithSdp(RTCClient client, SessionDescription sdp) {
        signalingManager.createOffer(sdp);
    }

    @Override
    public void rtcClientDidReceiveLocalVideoTrack(RTCClient client, VideoTrack videoTrack) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            // update the ui from here
            localVideoTrack.setValue(videoTrack);
        });
    }

    @Override
    public void rtcClientDidReceiveRemoteVideoTrack(RTCClient client, VideoTrack videoTrack) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            // update the ui from here
            remoteVideoTrack.setValue(videoTrack);
        });
    }

    @Override
    public void rtcClientDidReceiveError(RTCClient client, Error error) { }

    @Override
    public void rtcClientDidChangeConnectionState(RTCClient client, PeerConnection.IceConnectionState connectionState) {
        if (connectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
            client.disconnect();
        } else if (connectionState == PeerConnection.IceConnectionState.CONNECTED) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                // update the ui from here
                state.setValue(RTCClientState.CONNECTED);
            });

        }
    }

    @Override
    public void rtcClientDidChangeState(RTCClient client, RTCClientState clientState) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            // update the ui from here
            state.setValue(clientState);
        });
    }

    @Override
    public void rtcClientDidGenerateIceCandidate(RTCClient client, IceCandidate iceCandidate) {
        signalingManager.addCandidate(iceCandidate);
    }

    @Override
    public void rtcClientCreatedAnswer(RTCClient client, SessionDescription sdp) {
        signalingManager.updateAnswer(sdp);
    }

    //endregion

    //region SignalingManagerInterface
    @Override
    public void signalingManagerGotRemoteDescription(SessionDescription  description) {
        client.createAnswerForOfferReceived(description);
    }

    @Override
    public void signalingManagerGotRemoteIceCandidate(IceCandidate candidate) {
        client.addIceCandidate(candidate);
    }

    @Override
    public void signalingManagerGotRemoteOffer(SessionDescription offer) {
        client.createAnswerForOfferReceived(offer);
    }

    //endregion
}
