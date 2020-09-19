package com.avantica.videochat.rtc;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class RTCClient implements PeerConnection.Observer {

    //region Public properties
    public RTCClientInterface delegate;
    private Boolean isCreatingRoom = false;
    //endregion

    //region Private properties

    @Nullable
    private AudioTrack audioTrack;
    @Nullable
    private MediaStream localStream;
    @Nullable
    private PeerConnection peerConnection;
    private MediaConstraints callConstraint = new MediaConstraints();
    private List<IceCandidate> remoteIceCandidates = new ArrayList<IceCandidate>();
    private PeerConnectionFactory connectionFactory;
    private RTCClientState state = RTCClientState.DISCONNECTED;

    //region Init functions
    public RTCClient(Context context, RTCClientInterface delegate) {
        this.delegate = delegate;
        initMediaConstraints();
        initPeerConnectionFactory(context);
        initPeerConnection();
    }
    //endregion

    public void setState(RTCClientState state) {
        this.state = state;
        delegate.rtcClientDidChangeState(this, state);
    }

    private List<PeerConnection.IceServer> getPeerIceServers() {
        PeerConnection.IceServer server1 = PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer();
        PeerConnection.IceServer server2 = PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer();
        List<PeerConnection.IceServer> array = new ArrayList<PeerConnection.IceServer>();
        array.add(server1);
        array.add(server2);
        return array;

    }

    private void initMediaConstraints() {
        callConstraint.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        callConstraint.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
    }

    private void initPeerConnectionFactory(Context context) {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(context)
                .setEnableVideoHwAcceleration(true)
                .createInitializationOptions());

        connectionFactory = PeerConnectionFactory
                .builder()
                .createPeerConnectionFactory();
    }

    private void initPeerConnection() {
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(getPeerIceServers());
        peerConnection =
                connectionFactory.createPeerConnection(configuration, this);
    }

    //region Public API
    public void startConnection() {
        if (peerConnection == null) {
            return;
        }

        setState(RTCClientState.CONNECTING);
        localStream = generateLocalStream();
        peerConnection.addStream(localStream);

        if (!localStream.videoTracks.isEmpty()) {
            VideoTrack localVideoTrack = localStream.videoTracks.get(0);
            delegate.rtcClientDidReceiveLocalVideoTrack(this, localVideoTrack);
        }
    }

    //endregion

    public void disconnect() {
        if (peerConnection == null) {
            return;
        }

        peerConnection.close();

        if (localStream != null) {
            audioTrack = null;
            peerConnection.removeStream(localStream);
            localStream = null;
        }

        setState(RTCClientState.DISCONNECTED);
        isCreatingRoom = false;
        delegate.rtcClientDidChangeState(this, state);
    }

    public void makeOffer() {
        if (peerConnection == null) {
            return;
        }
        isCreatingRoom = true;
        peerConnection.createOffer(createOfferObserver, callConstraint);
    }

    public void createAnswerForOfferReceived(SessionDescription remoteDescription) {
        if (remoteDescription == null || peerConnection == null) {
            return;
        }
        // Add remote description
        peerConnection.setRemoteDescription(remoteDescriptionObserver, remoteDescription);

    }

    public void addIceCandidate(IceCandidate candidate) {
        // Set ice candidate after setting remote description
        if (peerConnection.getRemoteDescription() != null) {
            peerConnection.addIceCandidate(candidate);
        } else {
            remoteIceCandidates.add(candidate);
        }
    }

    public void muteCall(Boolean mute) {
        if (audioTrack != null) {
            audioTrack.setEnabled(!mute);
        }
    }

    //region Private helpers
    // Generate local stream and keep it live and add to new peer connection
    private MediaStream generateLocalStream() {

        MediaStream localStream = connectionFactory.createLocalMediaStream("RTCmS");


        VideoCapturer videoCapturer = createVideoCapturer();
        VideoSource videoSource = connectionFactory.createVideoSource(videoCapturer);
        VideoTrack videoTrack = connectionFactory.createVideoTrack("RTCvS0", videoSource);
        videoTrack.setEnabled(true);
        localStream.addTrack(videoTrack);
        videoCapturer.startCapture(1024, 640, 24);


        AudioSource audioSource = connectionFactory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = connectionFactory.createAudioTrack("RTCaS0", audioSource);
        this.audioTrack = audioTrack;
        localStream.addTrack(audioTrack);

        return localStream;
    }

    private VideoCapturer createVideoCapturer() {
        return createCameraCapturer(new Camera1Enumerator(false));
    }

    //endregion

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void handleGenericSessionError(String errorMessage) {
        delegate.rtcClientDidReceiveError(this, new Error(errorMessage));
    }

    private void handleSdpGenerated(SessionDescription description) {
        if (description == null) {
            return;
        }
        // set local description
        peerConnection.setLocalDescription(localDescriptionObserver, description);

        //  Signal to server to pass this sdp with for the session call
        delegate.rtcClientStartCallWithSdp(this, description);
    }

    private void handleRemoteDescriptionSet() {
        for (IceCandidate candidate : remoteIceCandidates) {
            peerConnection.addIceCandidate(candidate);
        }
        remoteIceCandidates.clear();
    }

    private void handleAnswerReceived(SessionDescription description) {
        if (description != null && peerConnection != null) {
            // Add remote description
            //When hosting
            if (isCreatingRoom) {
                peerConnection.setRemoteDescription(handleAnswerObserver, description);
            } else {
                //When connecting
                peerConnection.setLocalDescription(localDescriptionObserver, description);
                delegate.rtcClientCreatedAnswer(this, description);
            }

        }
    }

    //region PeerConnection.Observer
    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {

    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        if (iceConnectionState != null) {
            delegate.rtcClientDidChangeConnectionState(this, iceConnectionState);
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) { }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) { }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        if (iceCandidate != null) {
            delegate.rtcClientDidGenerateIceCandidate(this, iceCandidate);
        }

    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) { }
    //endregion

    @Override
    public void onAddStream(MediaStream mediaStream) {
        if (!mediaStream.videoTracks.isEmpty()) {
            delegate.rtcClientDidReceiveRemoteVideoTrack(this, mediaStream.videoTracks.get(0));
        }
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) { }

    @Override
    public void onDataChannel(DataChannel dataChannel) { }

    @Override
    public void onRenegotiationNeeded() { }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) { }
    //endregion

    //region SdpObservers

    private SdpObserver localDescriptionObserver = new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
            handleGenericSessionError(s);
        }

        @Override
        public void onSetFailure(String s) {
            handleGenericSessionError(s);
        }

    };

    private SdpObserver createOfferObserver = new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            handleSdpGenerated(sessionDescription);
        }

        @Override
        public void onSetSuccess() {

        }

        @Override
        public void onCreateFailure(String s) {
            handleGenericSessionError(s);
        }

        @Override
        public void onSetFailure(String s) {
            handleGenericSessionError(s);
        }
    };

    private SdpObserver handleAnswerObserver = new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
        }

        @Override
        public void onSetSuccess() {
            handleRemoteDescriptionSet();
            setState(RTCClientState.CONNECTED);

        }

        @Override
        public void onCreateFailure(String s) {
            handleGenericSessionError(s);
        }

        @Override
        public void onSetFailure(String s) {
            handleGenericSessionError(s);
        }
    };

    private SdpObserver createAnswerObserver = new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            handleAnswerReceived(sessionDescription);
            setState(RTCClientState.CONNECTED);
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
            handleGenericSessionError(s);
        }

        @Override
        public void onSetFailure(String s) {
            handleGenericSessionError(s);
        }

    };

    private SdpObserver remoteDescriptionObserver = new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {

        }

        @Override
        public void onSetSuccess() {
            handleRemoteDescriptionSet();
            peerConnection.createAnswer(createAnswerObserver, callConstraint);
        }

        @Override
        public void onCreateFailure(String s) {
            handleGenericSessionError(s);
        }

        @Override
        public void onSetFailure(String s) {
            handleGenericSessionError(s);
        }
    };
    //endregion
}
