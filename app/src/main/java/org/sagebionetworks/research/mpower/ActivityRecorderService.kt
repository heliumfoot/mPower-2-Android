/*
 * BSD 3-Clause License
 *
 * Copyright 2020  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.mpower

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Sets
import dagger.android.DaggerService
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import org.sagebionetworks.research.domain.async.AsyncActionConfiguration
import org.sagebionetworks.research.domain.async.DeviceMotionRecorderConfigurationImpl
import org.sagebionetworks.research.domain.async.MotionRecorderType
import org.sagebionetworks.research.domain.async.RecorderConfiguration
import org.sagebionetworks.research.domain.result.interfaces.Result
import org.sagebionetworks.research.domain.step.implementations.StepBase
import org.sagebionetworks.research.domain.step.interfaces.Step
import org.sagebionetworks.research.domain.task.navigation.NavDirection
import org.sagebionetworks.research.domain.task.navigation.TaskBase
import org.sagebionetworks.research.presentation.perform_task.TaskResultManager
import org.sagebionetworks.research.presentation.perform_task.TaskResultManager.TaskResultManagerConnection
import org.sagebionetworks.research.presentation.recorder.Recorder
import org.sagebionetworks.research.presentation.recorder.RecorderConfigPresentation
import org.sagebionetworks.research.presentation.recorder.RestartableRecorderConfiguration
import org.sagebionetworks.research.presentation.recorder.sensor.SensorRecorderConfigPresentationFactory
import org.sagebionetworks.research.presentation.recorder.service.RecorderService
import org.sagebionetworks.research.presentation.recorder.service.RecorderService.RecorderBinder
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class ActivityRecorderService : DaggerService(), ServiceConnection {
    private var isRecording = false

    @Inject
    lateinit var taskResultManager: TaskResultManager

    @Inject
    lateinit var recorderConfigPresentationFactory: SensorRecorderConfigPresentationFactory

    // private lateinit var recorderManager: RecorderManager

    private val asyncActions = mutableSetOf<AsyncActionConfiguration>(
        DeviceMotionRecorderConfigurationImpl.builder()
            .setIdentifier("passiveGait")
            .setStartStepIdentifier("start")
            .setStopStepIdentifier("stop")
            .setFrequency(null)
            .setRecorderTypes(mutableSetOf(
                MotionRecorderType.USER_ACCELERATION,
                MotionRecorderType.MAGNETIC_FIELD,
                MotionRecorderType.ROTATION_RATE,
                MotionRecorderType.GYROSCOPE
            ))
            .build()
    )

    private val startStep = PassiveGaitStep("start", asyncActions)

    private val stopStep = PassiveGaitStep("stop", asyncActions)

    private val steps = mutableListOf<Step>(startStep, stopStep)

    private val task = TaskBase.builder()
            .setIdentifier("PassiveGait")
            .setAsyncActions(asyncActions)
            .setSteps(steps)
            .build()

    private lateinit var taskResultManagerConnectionSingle: Single<TaskResultManagerConnection>
    private var binder: RecorderBinder? = null

    /**
     * Invariant: bound == true exactly when binder != null && service != null. binder == null exactly when service ==
     * null.
     */
    private var bound = false
    private var service: RecorderService? = null

    private lateinit var compositeDisposable: CompositeDisposable
    private lateinit var recorderConfigs: Set<RecorderConfigPresentation>

    private lateinit var taskRunUUID: UUID

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        setupRecorderManager()
    }

    private fun setupRecorderManager() {
        taskRunUUID = UUID.randomUUID()
        Log.d(TAG, "-- taskUUID: $taskRunUUID")
//        recorderManager = RecorderManager(task, TASK_IDENTIFIER, taskUUID, this.baseContext,
//                taskResultManager, recorderConfigPresentationFactory)

        taskResultManagerConnectionSingle = taskResultManager.getTaskResultManagerConnection(TASK_IDENTIFIER, taskRunUUID)
        compositeDisposable = CompositeDisposable()
        recorderConfigs = this.getRecorderConfigs()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: startId = $startId, isRecording = $isRecording")

        intent?.let {
            when (val event = it.getSerializableExtra(EVENT) as Event) {
                Event.STARTED_WALKING -> startRecording()
                Event.STOPPED_WALKING -> stopRecording()
                else -> Log.d(TAG, "EVENT is invalid: $event")
            }
        }

        return START_REDELIVER_INTENT
    }

    private fun timeToDateStr(dateInMs: Long): String {
        return if (dateInMs > 0) {
            SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(dateInMs))
        } else {
            ""
        }
    }

    //WALK START EVENT - Ignore if already recording
    // Call startForeground with notification to show user - need design from Design team
    // Create Task
    // Initialize RecorderManager
    // Record 30 seconds of walk data
    // Stop recording
    // Save to Bridge
    // Stop service
    private fun startRecording() {
        if (!isRecording) {
            val sharedPrefs = getSharedPreferences(TRANSITION_PREFS, Context.MODE_PRIVATE)
            val lastRecordedAt = sharedPrefs.getLong(LAST_RECORDED_AT, -1)
            val now = Date().time

            Log.d(TAG, "-- lastRecordedAt: ${timeToDateStr(lastRecordedAt)}, now: ${timeToDateStr(now)}")

            if (now - lastRecordedAt > MIN_FREQUENCY_MS) {
                Log.d(TAG, "-- startRecording ${timeToDateStr(now)}")
                isRecording = true
                startForeground()

                // NOTE: Currently RecorderService within RecorderManager is not bound, need to fix
                // don't care about navDirection
                // recorderManager.onStepTransition(null, startStep, 0)
                // onStepTransition(null, startStep, 0)
                val bindIntent = Intent(this, RecorderService::class.java)
                bindService(bindIntent, this, Context.BIND_AUTO_CREATE)
            } else {
                Log.d(TAG, "-- NOT RECORDING - ONLY RECORD ONCE EVERY $MIN_FREQUENCY_MS")
                stopSelf()
            }
        }
    }

    private fun startForeground() {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, getString(R.string.foreground_channel_id))
            .setContentTitle(getText(R.string.recording_notification_title))
            .setContentText(getText(R.string.recording_notification_message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val title = getString(R.string.foreground_channel_title)
            val desc = getString(R.string.foreground_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(getString(R.string.foreground_channel_id), title, importance)
            mChannel.description = desc
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    //WALK END EVENT - Ignore if recording is already done
    // Should not need to call startForeground as service is either already running or it wasn't and
    // this is just a NO-OP and a call to stopService.
    // Stop recording
    // Save to bridge - any recording is worth saving
    // Stop service
    private fun stopRecording() {
        if (isRecording) {
            Log.d(
                TAG,
                "-- stopRecording ${SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date())}"
            )
            isRecording = false
            // recorderManager.onStepTransition(stopStep, null, 0)
            onStepTransition(stopStep, null, null)

            val sharedPrefs = getSharedPreferences(TRANSITION_PREFS, Context.MODE_PRIVATE)
            sharedPrefs.edit().putLong(LAST_RECORDED_AT, Date().time).apply()
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        // unbindService(recorderManager)
        unbindService(this)
        super.onDestroy()
    }

    /**
     * Returns a map of Recorder Id to Recorder containing all of the recorders that are currently active. An active
     * recorder is any recorder that has been created and has not had stop() called on it.
     *
     * @return A map of Recorder Id to Recorder containing all of the active recorders.
     */
    private fun getActiveRecorders(): ImmutableMap<String?, Recorder<out Result?>> {
        return if (bound) {
            service!!.getActiveRecorders(this.taskRunUUID)
        } else {
            Log.w(TAG, "Cannot get active recorders Service is not bound")
            ImmutableMap.of()
        }
    }

    override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder?) {
        binder = (iBinder as RecorderBinder?)!!
        service = binder!!.service
        bound = true
        val activeRecorders: Map<String?, Recorder<out Result?>> = getActiveRecorders()
        try {
            for (config in recorderConfigs) {
                if (!activeRecorders.containsKey(config.identifier)) {
                    service!!.createRecorder(this.taskRunUUID, config)
                }
            }
            onStepTransition(null, startStep, null)
        } catch (e: IOException) {
            Log.w(TAG, "Encountered IOException while initializing recorders", e)
            // TODO rkolmos 8/13/2018 handle the IOException.
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName?) {
        binder = null
        service = null
        bound = false
        compositeDisposable.dispose()
    }

    /**
     * Starts, stops, and cancels the appropriate recorders in response to the step transition from previousStep to
     * nextStep in navDirection.
     *
     * @param previousStep The step that has just been transitioned away from, null indicates that nextStep is the first step.
     * @param nextStep     The step that has just been transition to, null indicates that previousStep is the last step.
     * @param navDirection The direction in which the transition from previousStep to nextStep occurred in.
     */
    private fun onStepTransition(previousStep: Step?, nextStep: Step?, @NavDirection navDirection: Int?) {
        Log.d(TAG,"onStepTransition called from: $previousStep, to: $nextStep in direction: $navDirection")
        val shouldStart: MutableSet<RecorderConfigPresentation> = HashSet()
        val shouldCancel: Set<RecorderConfigPresentation?> = HashSet()
        val shouldStop: MutableSet<RecorderConfigPresentation?> = HashSet()

        // There are a few scenarios I can think of that
        for (config in recorderConfigs) {
            val startStepIdentifier = config.startStepIdentifier
            val stopStepIdentifier = config.stopStepIdentifier

            // No matter the navigation direction, if we are leaving the stop step, we should stop the recorder
            if (previousStep != null && stopStepIdentifier == previousStep.identifier) {
                Log.d(TAG, "previousStep is stopStep, stopping recorder config " + config.identifier)
                // The recorder should be stopped. Since it's stop step identifier has just ended.
                shouldStop.add(config)
            }
            if (nextStep != null && startStepIdentifier != null && startStepIdentifier == nextStep.identifier) {
                Log.d(TAG,"nextStep is startStep, starting recorder config " + config.identifier)
                // The recorder should be started since we are navigating to it's start step.
                shouldStart.add(config)
            }

            // Did user navigate backwards?
            if (navDirection == NavDirection.SHIFT_RIGHT) {
                // There may be more scenarios we encounter as we add more complex navigation for recorders;
                // however, for now let's make sure that if we navigate backwards from the start step,
                // the recorder knows it should be stopped, because the previous step started it.
                if (previousStep != null && startStepIdentifier != null && startStepIdentifier == previousStep.identifier) {
                    shouldStop.add(config)
                }
            }
        }

        // recorders configured to cancel, or to both start and stop. let's not stop or start them
        val startAndStopOrCancel: Set<RecorderConfigPresentation?> = Sets
                .union(
                        Sets.intersection(shouldStart,
                                shouldStop), shouldCancel)
        if (bound) {
            val activeRecorders: Map<String?, Recorder<out Result?>> = getActiveRecorders()
            for (config in Sets.difference(
                    shouldStart, startAndStopOrCancel)) {
                var activeRecorder = activeRecorders[config.identifier]
                // This is important to call before creating the result because this may
                // re-create the recorder to prep for a proper recorder restart
                activeRecorder = validateRecorderStateBeforeStart(activeRecorder, config)
                if (activeRecorder != null) {
                    // Only wait for results of recorders which were started
                    taskResultManagerConnectionSingle.blockingGet()
                            .addAsyncActionResult(activeRecorder.result)
                    service!!.startRecorder(this.taskRunUUID, config.identifier)
                    Log.d(TAG, "Starting recorder " + config.identifier)
                } else {
                    // Recorder data will not be collected and uploaded here, but at least the app will not crash
                    Log.d(TAG, "Failed to restart recorder " + config.identifier)
                }
            }
            for (config in Sets.difference(shouldStop, startAndStopOrCancel)) {
                val identifier = config!!.identifier
                if (activeRecorders.containsKey(identifier)) {
                    if (activeRecorders[identifier]!!.isRecording) {
                        Log.d(TAG, "${this.taskRunUUID}, $identifier")
                        Log.d(TAG, "Stopping recorder " + config.identifier)
                    }
                }
            }
            for (config in shouldCancel) {
                val identifier = config!!.identifier
                if (activeRecorders.containsKey(identifier)) {
                    if (activeRecorders[identifier]!!.isRecording) {
                        Log.d(TAG, "Canceling recorder " + config.identifier)
                        service!!.cancelRecorder(this.taskRunUUID, identifier)
                    }
                }
            }
        } else {
            Log.w(TAG, "OnStepTransition was called but RecorderService was unbound.")
            // TODO: rkolmos 06/20/2018 handle the service being unbound
        }
    }

    /**
     * Validate the state of the recorder so we know it is ok to start it without having any restart complications.
     * @param recorder we will be starting
     * @param config the config of the recorder that will be starting
     */
    private fun validateRecorderStateBeforeStart(
            recorder: Recorder<out Result?>?,
            config: RecorderConfigPresentation): Recorder<out Result?>? {
        var recorder = recorder
        if (recorder == null) {
            // A null active recorder here means that the recorder has already run and been completed
            if (config is RestartableRecorderConfiguration) {
                check(config.shouldDeletePrevious) {
                    // To support Recorder restart with appending data functionality,
                    // We will need to somehow pass in a flag to ReactiveFileResultRecorder to
                    // signal it to open the outputFile for appending.
                    "RecorderManager cannot restart this recorder " +
                            "because getShouldDeletePrevious returns false and recorder appending is not supported yet."
                }

                // At this point, we know that the dev has configured the recorder properly to restart
                // and they are ok with the recorder file being replaced by the new one.
                // In this case, let's re-create the recorder to allow it to restart appropriately.
                try {
                    Log.i(TAG, "Recreating restartable recorder " + config.getIdentifier())
                    recorder = service!!.createRecorder(this.taskRunUUID, config)
                } catch (e: IOException) {
                    Log.e(TAG,"Encountered IOException while initializing recorder " + config.getIdentifier(), e)
                }
            } else {
                throw IllegalStateException("RecorderManager cannot restart a recorder unless it\'s " +
                        "configured as a RestartableRecorderConfiguration and specifies " +
                        "it should delete the previous data file when restarting")
            }
        }
        return recorder
    }

    private fun getRecorderConfigs(): Set<RecorderConfigPresentation> {
        val recorderConfigs: MutableSet<RecorderConfigPresentation> = HashSet()
        for (asyncAction in task.asyncActions) {
            if (asyncAction is RecorderConfiguration) {
                recorderConfigs.add(
                        recorderConfigPresentationFactory.create(asyncAction))
            }
        }
        return recorderConfigs
    }

    inner class PassiveGaitStep(identifier: String, asyncActions: Set<AsyncActionConfiguration>)
        : StepBase(identifier, asyncActions) {

        override fun copyWithIdentifier(identifier: String): PassiveGaitStep {
            throw UnsupportedOperationException("PassiveGait steps cannot be copied")
        }
    }

    enum class Event {
        STARTED_WALKING, STOPPED_WALKING
    }

    companion object {
        private const val TAG = "ActivityRecorderService"

        private const val TASK_IDENTIFIER = "PassiveGait"

        private const val TRANSITION_PREFS = "transitionPrefs"

        private const val LAST_RECORDED_AT = "lastRecordedAt"

        private const val TIME_PATTERN = "HH:mm:ss"

        private const val FOREGROUND_NOTIFICATION_ID = 100

        private const val MIN_FREQUENCY_MS: Long = 0 // 1000 * 60 * 3

        private const val MAX_RECORDING_TIME_MS: Long = 1000 * 30

        const val EVENT = "EVENT"
    }
}