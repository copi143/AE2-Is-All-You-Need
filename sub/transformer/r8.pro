-allowaccessmodification
-dontwarn **
-dontnote **
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keepnames class org.objectweb.asm.tree.ClassNode
-keep,allowoptimization class allyouneed.transformer.KeyInternTransformationService {
    public <init>();
    public <methods>;
}
-keep,allowoptimization class allyouneed.transformer.KeyInternClassTransformer {
    public <init>(...);
    public <methods>;
}
-keep class allyouneed.transformer.MarkedLogger { *; }
-keep,allowoptimization class allyouneed.transformer.SelfModLocator {
    public <init>();
    public <methods>;
}
-repackageclasses allyouneed.transformer.deps
