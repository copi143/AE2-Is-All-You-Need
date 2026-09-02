-allowaccessmodification
-dontwarn **
-dontnote **
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep,allowoptimization class allyouneed.transformer.AEKeyTransformationService {
    public <init>();
    public <methods>;
}
-keep,allowoptimization class allyouneed.transformer.AEKeyLaunchPluginService {
    public <init>();
    public <methods>;
}
-keep class allyouneed.transformer.RuntimeClasses {
    public static <methods>;
}
-keep class allyouneed.transformer.MarkedLogger { *; }
-keep,allowoptimization class allyouneed.transformer.SelfModLocator {
    public <init>();
    public <methods>;
}
-repackageclasses allyouneed.transformer.deps
