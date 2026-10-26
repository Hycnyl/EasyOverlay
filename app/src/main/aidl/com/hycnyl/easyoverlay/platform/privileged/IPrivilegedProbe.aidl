package com.hycnyl.easyoverlay.platform.privileged;

// All operations are read-only or hard-coded to EasyOverlay's own package.
interface IPrivilegedProbe {
    String foregroundSnapshot() = 1;
    String optimizationSnapshot() = 2;
    String applySelfOptimization(String originalSnapshot) = 3;
    String restoreSelfOptimization(String originalSnapshot) = 4;
    void destroy() = 16777114;
}
