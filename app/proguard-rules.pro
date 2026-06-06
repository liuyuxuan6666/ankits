# Add project specific ProGuard rules here.

# ZXing QR Scanner
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# sherpa-onnx JNI classes
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**
