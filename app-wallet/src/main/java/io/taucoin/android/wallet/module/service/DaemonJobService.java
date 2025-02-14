package io.taucoin.android.wallet.module.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Parcelable;
import android.support.annotation.RequiresApi;

import io.taucoin.android.wallet.MyApplication;
import io.taucoin.android.wallet.base.TransmitKey;
import io.taucoin.foundation.util.AppUtil;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DaemonJobService extends JobService {
    private static final int JOB_ID = 10;
    private static final int JOB_PERIODIC = 2 * 1000; // 2s
    private static final int BACKOFF_CRITERIA = 10 * 1000; // 10s

    public static void startJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, new ComponentName(context.getPackageName(), DaemonJobService.class.getName()));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Execute job every 10s
            builder.setPeriodic(JOB_PERIODIC);
        } else {
            // Delayed execution of tasks
            builder.setMinimumLatency(JOB_PERIODIC);
            // Setting deadline will start execution if the deadline has not met the required conditions
            builder.setOverrideDeadline(JOB_PERIODIC);
        }
        // Setting Network Conditions
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        // Linear Retry Scheme
        builder.setBackoffCriteria(BACKOFF_CRITERIA, JobInfo.BACKOFF_POLICY_LINEAR);

        if (jobScheduler != null) {
            jobScheduler.schedule(builder.build());
        }
    }

    public static void closeJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.cancel(JOB_ID);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        //If more than 7.0 rounds of training
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startJob(this);
        }

        //Combining two-process guardianship
        boolean isLocalRun = AppUtil.isServiceRunning(this, TxService.class.getName());
        boolean isRemoteRun = AppUtil.isServiceRunning(this, RemoteService.class.getName());
        if (!isLocalRun || !isRemoteRun) {
            TxService.startTxService(TransmitKey.ServiceType.GET_HOME_DATA);

            Intent intent =  new Intent(this, RemoteService.class);
            Parcelable parcelable = NotifyManager.getInstance().getNotifyData();
            intent.putExtra("bean", parcelable);
            startService(intent);
            if(!isRemoteRun){
                MyApplication.getRemoteConnector().init();
            }
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
