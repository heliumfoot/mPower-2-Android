package org.sagebionetworks.research.mpower.inject;

import android.content.Context;

import com.google.gson.Gson;

import org.sagebionetworks.research.domain.repository.TaskRepository;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AppDataModule {
    @Provides
    @Singleton
    TaskRepository provideTaskRepository(Context context, Gson gson) {
        return new AppResourceTaskRepository(context, gson);
    }
}
