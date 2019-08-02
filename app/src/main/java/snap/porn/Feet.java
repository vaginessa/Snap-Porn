package snap.porn;

import android.graphics.Bitmap;
import java.io.File;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.graphics.BitmapFactory;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodReplacement;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import java.text.SimpleDateFormat;
import android.net.Uri;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.io.Flushables;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.UUID;
/*
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.util.Matrix;
*/

public class Feet implements IXposedHookLoadPackage {
    AtomicBoolean hasHooked = new AtomicBoolean();
    String replaceLocation = "/storage/emulated/0/Snapchat/";
    String SaveLocation = "/storage/emulated/0/Saved/";
    BitmapFactory.Options bitmapOptions = new android.graphics.BitmapFactory.Options();
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android") || hasHooked.getAndSet(true))
            return;

        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        findAndHookMethod("android.app.Application", lpparam.classLoader, "attach", android.content.Context.class, new XC_MethodHook() { // snapchat is a multidex application, wait for it to be attached
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                findAndHookMethod("aolu", lpparam.classLoader, "a",  XC_MethodReplacement.DO_NOTHING); // Screenshot Bypass
                findAndHookMethod("acnb", lpparam.classLoader, "a", "fjx", Integer.class, String.class, long.class, boolean.class, int.class, "fjw$b", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("Image taken. Proceeding with hook.");

                        File jpg = new File(replaceLocation + "replace.jpg");
                        if (jpg.exists()) {
                            Bitmap replace = BitmapFactory.decodeFile(jpg.getPath(), bitmapOptions);
                            param.args[0] = rotateBitmap(replace, -90);

                            File findAvailable = new File(replaceLocation + "replaced.jpg");
                            int index = 0;

                            while(findAvailable.exists()) {
                                findAvailable = new File(replaceLocation + "replaced" + index++ + ".jpg");
                            }
                            jpg.renameTo(findAvailable);
                            XposedBridge.log("Replaced image.");
                        } else XposedBridge.log("Nothing to replace");
                    }
                });

                findAndHookMethod("acnc", lpparam.classLoader,  "a", Uri.class, int.class, boolean.class, "apmn", long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("Video taken. Proceeding with hook.");
                        Uri uri = (Uri) param.args[0];
                        File recordedVideo = new File(uri.getPath());
                        File videoToShare =  new File(replaceLocation + "replace.mp4");

                        // Sometimes, the video rotation is wrong. I wrote some code to rotate it to the correct angle,
                        // however the angle is correct more often than not so I won't use this until I figure out a way to pick whether to rotate our not
                        //
                        // File rotatedShareVideo = rotateMp4File(videoToShare);

                        if (videoToShare.exists()) {
                            Files.copy(videoToShare, recordedVideo);
                            File findAvailable = new File(replaceLocation + "replaced.mp4");
                            int index = 0;

                            while(findAvailable.exists()) {
                                findAvailable = new File(replaceLocation + "replaced" + index++ + ".mp4");
                            }
                            videoToShare.renameTo(findAvailable);
                            XposedBridge.log("Replaced Video.");
                        } else XposedBridge.log("Nothing to replace");
                    }
                });

                findAndHookMethod("aivz", lpparam.classLoader, "e", new XC_MethodHook() { // video chat saving
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object metadata = getObjectField(param.thisObject, "c"); // akqb

                        Uri video = (Uri) callMethod(metadata, "aU_");

                        String username = (String) XposedHelpers.getObjectField(metadata, "s");
                        Long timestamp = (long) XposedHelpers.callMethod(metadata, "az_");
                        String key = getOrCreateKey(metadata); //may be wrong

                        java.util.Map<String, Object> isZippedMap = (java.util.Map) XposedHelpers.callMethod(metadata, "aK");
                        Boolean isZipped = (Boolean) isZippedMap.get("is_zipped"); // should be same thing as getting akwf.c.N, but for some reason it returns a long instead of a boolean

                        String readableTimestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", java.util.Locale.getDefault()).format(timestamp);
                        String savePath = SaveLocation + username + "/" + username + "." + readableTimestamp + "-CHAT-" + key + (isZipped ? ".zip" : ".mp4");
                        File saveFile = new File(savePath);

                        File saveFolder = new File(SaveLocation + username);
                        saveFolder.mkdirs();

                        Files.copy(new File(video.getPath()), saveFile);

                        if (isZipped) {
                            String unzipPath = SaveLocation + username + "/" + username + "." + "-CHAT-" + readableTimestamp + key;
                            unzipMedia(new FileInputStream(saveFile), unzipPath);
                        }

                        XposedHelpers.setAdditionalInstanceField(metadata, "CHAT_METADATA_ISVIDEO", true);
                    }
                });//u

                findAndHookMethod("aidh", lpparam.classLoader, "b", "aipx", new XC_MethodHook() { // image chat saving
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object metadata = param.args[0];

                        String key = getOrCreateKey(metadata);
                        Boolean isVideo = false;
                        try {
                            isVideo = (boolean) getAdditionalInstanceField(metadata, "CHAT_METADATA_ISVIDEO");
                        } catch (NullPointerException ignored) {}

                        if (isVideo) return; // If it's a video, we've already hooked it through akwf

                        String username = (String) XposedHelpers.getObjectField(metadata, "s");
                        Long timestamp = (long) XposedHelpers.callMethod(metadata, "az_");

                        Object encryptor = param.getResult();

                        XposedBridge.log("key: " + key);
                        XposedBridge.log("isVideo: " + isVideo);
                        XposedBridge.log("username: " + username);
                        XposedBridge.log("timestamp: " + timestamp);

                        setAdditionalInstanceField(encryptor, "SNAP_KEY", key);
                        setAdditionalInstanceField(encryptor, "SNAP_ISVIDEO", isVideo);
                        setAdditionalInstanceField(encryptor, "SNAP_ISZIPPED", false);
                        setAdditionalInstanceField(encryptor, "SNAP_AUTHOR", username);
                        setAdditionalInstanceField(encryptor, "SNAP_TIMESTAMP", timestamp);

                        // known issue: discovery stories that are shared (like the ones from brother/mashable) are saved
                        // not only are they saved, but they're also saved as zips even though they aren't zipped
                        // normal stories that are shared are also saved, however they're saved in the correct format
                    }
                });//u

                findAndHookMethod("akuo", lpparam.classLoader, "a", "aqtb)", String.class, new XC_MethodHook() { // store metadata for direct snap saving
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable { // direct snap sharing
                        Object metadata = param.args[0];

                        String key = getOrCreateKey(metadata);
                        Boolean isVideo = (boolean) XposedHelpers.callMethod(metadata, "aU_");
                        Boolean isZipped = (boolean) XposedHelpers.callMethod(metadata, "");

                        String username = (String) XposedHelpers.getObjectField(metadata, "s");
                        Long timestamp = (long) XposedHelpers.callMethod(metadata, "az_");

                        Object cryptoHolder = param.getResult();
                        Object encryptor = XposedHelpers.getObjectField(cryptoHolder, "c");

                        setAdditionalInstanceField(encryptor, "SNAP_KEY", key);
                        setAdditionalInstanceField(encryptor, "SNAP_ISVIDEO", isVideo);
                        setAdditionalInstanceField(encryptor, "SNAP_ISZIPPED", isZipped);
                        setAdditionalInstanceField(encryptor, "SNAP_AUTHOR", username);
                        setAdditionalInstanceField(encryptor, "SNAP_TIMESTAMP", timestamp);
                    }
                });

                findAndHookMethod("com.snapchat.android.framework.crypto.CbcEncryptionAlgorithm", lpparam.classLoader, "b", InputStream.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable { // direct snap saving
                        String key = (String) getAdditionalInstanceField(param.thisObject, "SNAP_KEY");
                        if (key == null) return;

                        Boolean isVideo = (boolean) getAdditionalInstanceField(param.thisObject, "SNAP_ISVIDEO");
                        Boolean isZipped = (boolean) getAdditionalInstanceField(param.thisObject, "SNAP_ISZIPPED");
                        String username = (String) getAdditionalInstanceField(param.thisObject, "SNAP_AUTHOR");
                        Long timestamp = (long) getAdditionalInstanceField(param.thisObject, "SNAP_TIMESTAMP");

                        Boolean isChatMedia = false;
                        try {
                            isChatMedia = (boolean) getAdditionalInstanceField(param.thisObject, "SNAP_ISCHAT");
                        } catch (NullPointerException ignored) {}

                        InputStream stream = (InputStream) param.getResult();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        try {
                            ByteStreams.copy(stream, output);
                        } catch (IOException e) {
                            XposedBridge.log(e);
                        }
                        ByteArrayInputStream copiedInputStream = new ByteArrayInputStream(output.toByteArray());
                        param.setResult(copiedInputStream);

                        String readableTimestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", java.util.Locale.getDefault()).format(timestamp);
                        String savePath = SaveLocation + username + "/" + username + "." + readableTimestamp + (isChatMedia ? ("-CHAT-" + key) : key.hashCode()) + (isZipped ? ".zip" : (isVideo ? ".mp4" : ".jpg"));

                        File saveFolder = new File(SaveLocation + username);
                        saveFolder.mkdirs();

                        File saveFile = new File(savePath);
                        if (saveFile.exists()) return;

                        streamCopy(output, new FileOutputStream(saveFile));

                        if (isZipped) {
                            String unzipPath = SaveLocation + username + "/" + username + "." + readableTimestamp + (isChatMedia ? ("-CHAT-" + key) : key.hashCode());
                            unzipMedia(new FileInputStream(saveFile), unzipPath);
                        }
                    }
                });

                findAndHookMethod("anyg", lpparam.classLoader,  "a", "zq", int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        setObjectField(param.thisObject, "d", false);
                    }
                });//u

                // File exportTemp = new File(replaceLocation + "/export_temp.mp4");
                // if (exportTemp.exists()) exportTemp.delete();

                XposedBridge.log("Hooked (2.0)");
                XposedBridge.log("Hooked (2.0)");
                XposedBridge.log("Hooked (2.0)");
            }
        });

    }

    private String getOrCreateKey(Object obj) {
        if (obj == null)
            return null;

        String key = (String) XposedHelpers.getAdditionalInstanceField(obj, "SNAP_KEY");

        if (key == null) {
            key = UUID.randomUUID().toString();
            XposedHelpers.setAdditionalInstanceField(obj, "SNAP_KEY", key);
        }

        return key;
    }


    private Bitmap rotateBitmap(Bitmap src, int ang) { // rotate bitmap to fix image rotation bug when sharing
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(ang);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

/*
    private File rotateMp4(File mp4) {
        try {
            Movie movie = MovieCreator.build(mp4.getAbsolutePath());
            movie.setMatrix(Matrix.ROTATE_270);
            File export = new File(replaceLocation + "/export_temp.mp4");

            WritableByteChannel exportChannel = new FileOutputStream(export).getChannel();
            new DefaultMp4Builder().build(movie).writeContainer(exportChannel);
            return export;
        } catch (IOException e) {
            XposedBridge.log("Failed to rotate shared video");
            XposedBridge.log(e);
        }
    }
*/

    private static boolean streamCopy(ByteArrayOutputStream byteOutput, OutputStream targetStream) { // direct snap saving: copy stream
        Closer closer = Closer.create();

        try {
            closer.register(byteOutput);
            closer.register(targetStream);
            byteOutput.writeTo(targetStream);

            Flushables.flushQuietly(byteOutput);
            Flushables.flushQuietly(targetStream);

            return true;
        } catch (IOException e) {
            XposedBridge.log(e);
        } finally {
            try {
                closer.close();
            } catch (IOException e) {
                XposedBridge.log(e);
            }
        }

        return false;
    }

    private void unzipMedia(FileInputStream media, String savePath) { // video media that has an overlay come in as a zip, method to unzip all media and add the appropriate file extensions
        Closer closer = Closer.create();

        try {
            ZipInputStream zipStream = closer.register(new ZipInputStream(media));

            ZipEntry currentEntry = zipStream.getNextEntry();
            while (currentEntry != null) {
                if (currentEntry.getName().contains("media~")) {
                    File saveTo = new File(savePath + ".mp4");
                    int index = 0;

                    while (saveTo.exists()) {
                        saveTo = new File(savePath + index++ + ".mp4");
                    }
                    ByteStreams.copy(zipStream, closer.register(new FileOutputStream(saveTo)));
                }

                if (currentEntry.getName().contains("overlay~")) {
                    File saveTo = new File(savePath + ".jpg");
                    int index = 0;

                    while (saveTo.exists()) {
                        saveTo = new File(savePath + index++ + ".jpg");
                    }
                    ByteStreams.copy(zipStream, closer.register(new FileOutputStream(saveTo)));
                }
                currentEntry = zipStream.getNextEntry();
            }

        } catch (IOException e) {
            XposedBridge.log("Failed to unzip zipped media");
            XposedBridge.log(e);
        } finally {
            try {
                closer.close();
            } catch (IOException ignored) {}
        }
    }


}