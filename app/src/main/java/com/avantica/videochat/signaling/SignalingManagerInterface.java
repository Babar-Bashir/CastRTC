package com.avantica.videochat.signaling;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public interface SignalingManagerInterface {

    void signalingManagerGotRemoteDescription(SessionDescription description);
    void signalingManagerGotRemoteIceCandidate(IceCandidate candidate);

    void signalingManagerGotRemoteOffer(SessionDescription offer);
}
