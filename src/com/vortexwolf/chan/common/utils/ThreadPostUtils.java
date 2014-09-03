package com.vortexwolf.chan.common.utils;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Debug;
import android.text.format.DateFormat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.vortexwolf.chan.R;
import com.vortexwolf.chan.activities.ImageGalleryActivity;
import com.vortexwolf.chan.asynctasks.DownloadFileTask;
import com.vortexwolf.chan.common.Constants;
import com.vortexwolf.chan.common.Factory;
import com.vortexwolf.chan.common.library.DialogDownloadFileView;
import com.vortexwolf.chan.interfaces.IBitmapManager;
import com.vortexwolf.chan.models.domain.PostModel;
import com.vortexwolf.chan.models.presentation.AttachmentInfo;
import com.vortexwolf.chan.models.presentation.ThumbnailViewBag;
import com.vortexwolf.chan.services.BrowserLauncher;
import com.vortexwolf.chan.services.ThreadImagesService;
import com.vortexwolf.chan.settings.ApplicationSettings;

public class ThreadPostUtils {
    private static final Pattern dateTextPattern = Pattern.compile("^[а-я]+ (\\d+) ([а-я]+) (\\d+) (\\d{2}):(\\d{2}):(\\d{2})$", Pattern.CASE_INSENSITIVE);
    private static final String[] sMonthNames = new String[] { "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг",
            "Сен", "Окт", "Ноя", "Дек" };

    private static long sMaxVmHeap = Runtime.getRuntime().maxMemory() / 1024;
    private static long sHeapPad = 1024;
    
    private static final List<String> sWakabaBoards = Arrays.asList(new String[] { 
        "f", "i", "int", "o"
    });
    public static String getDateFromTimestamp(Context context, long timeInMiliseconds, TimeZone timeZone) {
        java.text.DateFormat dateFormat = DateFormat.getDateFormat(context);
        dateFormat.setTimeZone(timeZone);

        java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
        timeFormat.setTimeZone(timeZone);

        Date date = new Date(timeInMiliseconds);
        return dateFormat.format(date) + ", " + timeFormat.format(date);
    }

    public static String getMoscowDateFromTimestamp(Context context, long timeInMiliseconds) {
        return getDateFromTimestamp(context, timeInMiliseconds, TimeZone.getTimeZone("GMT+4"));
    }

    public static String getLocalDateFromTimestamp(Context context, long timeInMiliseconds) {
        return getDateFromTimestamp(context, timeInMiliseconds, TimeZone.getDefault());
    }

    //text = "Птн 12 Апр 2013 12:37:12";
    public static long parseMoscowTextDate(String text) {
        if (StringUtils.isEmpty(text)) {
            return 0;
        }

        Matcher m = dateTextPattern.matcher(text);
        if (!m.find()) {
            return 0;
        }

        int day = Integer.valueOf(m.group(1));
        String monthStr = m.group(2);
        int monthIndex = Arrays.asList(sMonthNames).indexOf(monthStr);
        int month = monthIndex != -1 ? monthIndex : 0;
        int year = Integer.valueOf(m.group(3));
        int hour = Integer.valueOf(m.group(4));
        int minute = Integer.valueOf(m.group(5));
        int second = Integer.valueOf(m.group(6));

        Date date = new Date(year - 1900, month, day, hour, minute, second);

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.HOUR, -4); // from GMT+4 to UTC

        long result = cal.getTimeInMillis();
        return result;
    }

    /**
     * Удаляет ссылки на другие сообщения из поста, если они расположены вначале
     * строки
     */
    public static String removeLinksFromComment(String comment) {
        String result = comment.replaceAll("(^|\n)(>>\\d+(\n|\\s)?)+", "$1");

        return result;
    }

    /** Проверяет, прикреплен ли к посту какой-либо файл */
    public static boolean hasAttachment(PostModel item) {
        return item.getAttachments().size() > 0;
    }
    
    public static int getAttachmentsNumber(PostModel item) {
        return item.getAttachments().size();
    }

    public static void openAttachment(final AttachmentInfo attachment, final Context context) {
        if (attachment == null) {
            return;
        }

        int imageSize = attachment.getSize();
        String url = attachment.getSourceUrl();

        long allocatedSize = Debug.getNativeHeapAllocatedSize() / 1024 + imageSize + sHeapPad;
        if (allocatedSize > sMaxVmHeap) {
            long freeSize = Math.max(0, imageSize - (allocatedSize - sMaxVmHeap));
            AppearanceUtils.showToastMessage(context, "Image is " + imageSize + "Kb. Available Memory is " + freeSize + "Kb");
            return;
        }

        Uri uri = Uri.parse(url);
        ThreadImagesService imagesService = Factory.resolve(ThreadImagesService.class);
        ApplicationSettings settings = Factory.resolve(ApplicationSettings.class);
        if (!settings.isLegacyImageViewer() && imagesService.hasImage(attachment.getThreadUrl(), url)) {
            // open a gallery activity
            Intent imageGallery = new Intent(context, ImageGalleryActivity.class);
            imageGallery.setData(uri);
            imageGallery.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            imageGallery.putExtra(Constants.EXTRA_THREAD_URL, attachment.getThreadUrl());
            context.startActivity(imageGallery);
        } else {
        	boolean done = false;
        	if (!UriUtils.isImageUri(uri)) {
        		switch (settings.getVideoPreviewMethod()) {
        		case 2:
        			done = true;
        			new DownloadFileTask(context, uri, null, new DialogDownloadFileView(context){
        				@Override
						public void showSuccess(File file) { play(file); }
						@Override
						public void showFileExists(File file) { play(file); }
						private void play(File file) {
							String type = "*/*";
							if ("webm".equalsIgnoreCase(attachment.getSourceExtension())) type = "video/*";
							Intent intent = new Intent(Intent.ACTION_VIEW);
        					intent.setDataAndType(Uri.fromFile(file), type);
        					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
        		        	context.startActivity(intent);
						}
        			}, true).execute();
        			break;
        		case 1:
        			url = url.replace(settings.getDomainUri().toString(), "https://2ch.pm/");
        			break;
        		case 0:
        			break;
        		}
        	}
        	if (!done) BrowserLauncher.launchInternalBrowser(context, url);
        }
    }

    /** Отображение комбобокса с политическими взглядами */
    public static boolean isPoliticsBoard(String boardName) {
        boolean hasPoliticsView = boardName.equals("po");

        return hasPoliticsView;
    }

    /** Будет отображать другим цветом посты после бамплимита */
    public static int getBumpLimitNumber(String boardName) {
        if (boardName.equals("vg")) {
            return 1000;
        }

        return 500;
    }
    
    public static boolean isMakabaBoard(String boardName) {
        return sWakabaBoards.indexOf(boardName) == -1;
    }
    
    public static void refreshAttachmentView(boolean isBusy, AttachmentInfo attachment, ThumbnailViewBag thumbnailView) {
        if (attachment == null || attachment.isEmpty()) {
            thumbnailView.hide();
            return;
        }
        
        thumbnailView.container.setVisibility(View.VISIBLE);
        thumbnailView.info.setText(attachment.getDescription());
        refreshAttachmentImage(isBusy, attachment, thumbnailView.image);
    }
    
    public static void setNonBusyAttachment(AttachmentInfo attachment, ImageView imageView) {
        if (ThreadPostUtils.shouldLoadFromWeb(attachment)) {
            ThreadPostUtils.refreshAttachmentImage(false, attachment, imageView);
        }
    }

    private static void refreshAttachmentImage(boolean isBusy, final AttachmentInfo attachment, ImageView imageView) {
        // clear the image content
        imageView.setImageResource(android.R.color.transparent);         
        imageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Обработчик события нажатия на картинку
                ThreadPostUtils.openAttachment(attachment, v.getContext());
            }
        });
        
        if (isBusy && shouldLoadFromWeb(attachment)) {
            return;
        }

        String thumbnailUrl = attachment.getThumbnailUrl();
        // Также добавляем уменьшенное изображение, нажатие на которое
        // открывает файл в полном размере
        if (thumbnailUrl != null) {
            // Ничего не загружаем, если так установлено в настройках
            ApplicationSettings settings = Factory.resolve(ApplicationSettings.class);
            IBitmapManager bitmapManager = Factory.resolve(IBitmapManager.class);
            if (settings.isLoadThumbnails() || bitmapManager.isCached(thumbnailUrl)) {
                bitmapManager.fetchBitmapOnThread(thumbnailUrl, imageView, null, R.drawable.error_image);
            } else if (!settings.isLoadThumbnails()) {
                imageView.setImageResource(R.drawable.empty_image);
            }
        } else {
            // Иногда можно прикреплять файлы с типом mp3, swf и пр., у
            // которых thumbnail=null. Нужно нарисовать другую картинку в
            // таких случаях
            if (attachment.isFile()) {
                imageView.setImageResource(attachment.getDefaultThumbnail());
            } else {
                imageView.setImageResource(R.drawable.error_image);
            }
        }
    }
    
    private static boolean shouldLoadFromWeb(AttachmentInfo attachment) {
        if (attachment == null || attachment.isEmpty()) {
            return false;
        }
        
        ApplicationSettings settings = Factory.resolve(ApplicationSettings.class);
        IBitmapManager bitmapManager = Factory.resolve(IBitmapManager.class);
        
        String thumbnailUrl = attachment.getThumbnailUrl();
        return thumbnailUrl != null && settings.isLoadThumbnails() && !bitmapManager.isCached(thumbnailUrl);
    }
}