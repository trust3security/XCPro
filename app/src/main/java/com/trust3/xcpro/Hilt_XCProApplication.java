package com.trust3.xcpro;

import android.app.Application;
import dagger.hilt.android.internal.managers.ApplicationComponentManager;
import dagger.hilt.android.internal.managers.ComponentSupplier;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.internal.GeneratedComponentManagerHolder;
import dagger.hilt.internal.UnsafeCasts;

/**
 * Runtime Hilt application base used by XCProApplication.
 *
 * <p>Build output currently provides nested helper classes without a generated host class for
 * this type. Keeping this host in source ensures dexing has a concrete nest host and preserves
 * normal Hilt application injection semantics.
 */
public abstract class Hilt_XCProApplication extends Application
        implements GeneratedComponentManagerHolder {

    private final ApplicationComponentManager componentManager =
            new ApplicationComponentManager(new AppComponentSupplier(this));
    private boolean injected = false;

    @Override
    public final Object generatedComponent() {
        return componentManager.generatedComponent();
    }

    public final ApplicationComponentManager componentManager() {
        return componentManager;
    }

    @Override
    public void onCreate() {
        inject();
        super.onCreate();
    }

    protected void inject() {
        if (!injected) {
            injected = true;
            ((XCProApplication_GeneratedInjector) generatedComponent())
                    .injectXCProApplication(UnsafeCasts.<XCProApplication>unsafeCast(this));
        }
    }

    private static final class AppComponentSupplier implements ComponentSupplier {
        private final Hilt_XCProApplication application;

        private AppComponentSupplier(Hilt_XCProApplication application) {
            this.application = application;
        }

        @Override
        public Object get() {
            try {
                Class<?> componentClass =
                        Class.forName("com.trust3.xcpro.DaggerXCProApplication_HiltComponents_SingletonC");
                Object builder = componentClass.getMethod("builder").invoke(null);
                Class<?> builderClass = builder.getClass();
                builderClass
                        .getMethod("applicationContextModule", ApplicationContextModule.class)
                        .invoke(builder, new ApplicationContextModule(application));
                return builderClass.getMethod("build").invoke(builder);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "Unable to create Hilt application component",
                        exception
                );
            }
        }
    }
}
