package kreyj.konfplan.util;

import io.quarkus.qute.TemplateExtension;
import jakarta.inject.Singleton;
import kreyj.konfplan.infrastructure.AssetManifest;

@Singleton
@TemplateExtension
public class AssetExtensions {
    private final AssetManifest manifest;
    private static AssetExtensions INSTANCE = null;


    public AssetExtensions(AssetManifest manifest) {
        this.manifest = manifest;
        if (null == INSTANCE) {
            INSTANCE = this;
        }
    }


    static String asset(String entry) {
        return INSTANCE.manifest.asset(entry);
    }


    static boolean isDev() {
        return INSTANCE.manifest.isDev();
    }
}
