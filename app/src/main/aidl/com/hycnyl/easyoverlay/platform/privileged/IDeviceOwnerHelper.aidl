package com.hycnyl.easyoverlay.platform.privileged;

interface IDeviceOwnerHelper {
    void onStart() = 1;
    void onStop() = 2;
    String notificationSnapshot() = 3;
    String setOwnNotificationGrant(int grantState) = 4;
    String restoreOwnNotificationGrant(String originalSnapshot) = 5;
}
