package com.novoda.downloadmanager;

import android.support.annotation.WorkerThread;

import com.novoda.notils.logger.simple.Log;

import static com.novoda.downloadmanager.DownloadBatchStatus.Status.DOWNLOADED;
import static com.novoda.downloadmanager.NotificationCustomizer.NotificationStackState.STACK_NOTIFICATION_DISMISSIBLE;
import static com.novoda.downloadmanager.NotificationCustomizer.NotificationStackState.STACK_NOTIFICATION_NOT_DISMISSIBLE;

class NotificationDispatcher {

    private static final boolean NOTIFICATION_SEEN = true;
    private final Object waitForDownloadService;
    private final NotificationCreator<DownloadBatchStatus> notificationCreator;
    private final DownloadsNotificationSeenPersistence notificationSeenPersistence;

    private DownloadService downloadService;

    NotificationDispatcher(Object waitForDownloadService,
                           NotificationCreator<DownloadBatchStatus> notificationCreator,
                           DownloadsNotificationSeenPersistence notificationSeenPersistence) {
        this.waitForDownloadService = waitForDownloadService;
        this.notificationCreator = notificationCreator;
        this.notificationSeenPersistence = notificationSeenPersistence;
    }

    @WorkerThread
    void updateNotification(DownloadBatchStatus downloadBatchStatus) {
        WaitForDownloadService.<Void>waitFor(downloadService, waitForDownloadService)
                .thenPerform(executeUpdateNotification(downloadBatchStatus));
    }

    private WaitForDownloadService.ThenPerform.Action<Void> executeUpdateNotification(DownloadBatchStatus downloadBatchStatus) {
        return () -> {
            NotificationInformation notificationInformation = notificationCreator.createNotification(downloadBatchStatus);
            DownloadBatchStatus.Status status = downloadBatchStatus.status();

            if (downloadBatchStatus.notificationSeen()) {
                Log.v("DownloadBatchStatus:", downloadBatchStatus.getDownloadBatchId(), "notification has already been seen.");
                return null;
            }
            downloadService.dismissStackedNotification(notificationInformation);

            if (status == DOWNLOADED) {
                notificationSeenPersistence.updateNotificationSeenAsync(downloadBatchStatus.getDownloadBatchId(), NOTIFICATION_SEEN);
            }

            if (notificationInformation.notificationStackState() == STACK_NOTIFICATION_DISMISSIBLE) {
                downloadService.stackNotification(notificationInformation);
            } else if (notificationInformation.notificationStackState() == STACK_NOTIFICATION_NOT_DISMISSIBLE) {
                downloadService.stackNotificationNotDismissible(notificationInformation);
            } else {
                downloadService.updateNotification(notificationInformation);
            }

            return null;
        };
    }

    void setDownloadService(DownloadService downloadService) {
        this.downloadService = downloadService;
    }
}
