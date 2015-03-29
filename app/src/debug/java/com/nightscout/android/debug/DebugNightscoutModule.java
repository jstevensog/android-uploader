package com.nightscout.android.debug;

import android.app.Application;

import com.nightscout.android.exceptions.FeedbackDialog;
import com.nightscout.android.exceptions.StubbedFeedbackDialog;
import com.nightscout.android.modules.NightscoutModule;
import com.nightscout.android.ui.AppContainer;
import com.nightscout.android.ui.MonitorFragment;
import com.nightscout.android.ui.NightscoutNavigationDrawer;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(
        addsTo = NightscoutModule.class,
        injects = {
                MonitorFragment.class,
                DebugAppContainer.class,
                NightscoutNavigationDrawer.class
        },
        overrides = true
)
public final class DebugNightscoutModule {
    @Provides
    @Singleton
    AppContainer provideAppContainer(DebugAppContainer debugAppContainer) {
        return debugAppContainer;
    }

    @Provides
    @Singleton
    FeedbackDialog providesReporter(Application app) {
//        ACRA.init(app);
//        ACRA.getErrorReporter().putCustomData("timezone", TimeZone.getDefault().getID());
//        return new AcraFeedbackDialog();
        return new StubbedFeedbackDialog(app);
    }
}
