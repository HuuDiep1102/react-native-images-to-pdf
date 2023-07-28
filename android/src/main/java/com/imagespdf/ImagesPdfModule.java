package com.imagespdf;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.graphics.Color;
import android.util.Base64;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@ReactModule(name = ImagesPdfModule.NAME)
public class ImagesPdfModule extends ReactContextBaseJavaModule {
    public static final String NAME = "ImagesPdf";

    public ImagesPdfModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void createPdf(ReadableMap options, Promise promise) {
        try {
            ReadableArray imagePaths = options.getArray("imagePaths");
            String outputFilename = options.getString("outputFilename");

            if (imagePaths.size() == 0) {
                throw new Exception("imagePaths is empty.");
            }

            PdfDocument pdfDocument = new PdfDocument();

            try {
                for (int i = 0; i < imagePaths.size(); ++i) {
                    String imagePath = imagePaths.getString(i);
                    Bitmap bitmap = getBitmapFromPathOrUri(imagePath);

                    if (bitmap == null) {
                        throw new Exception(imagePath + " cannot be decoded into a bitmap.");
                    }

                    int pageWidth = 596;
                    int pageHeight = 842;

                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo
                            .Builder(pageWidth, pageHeight, i + 1)
                            .create();

                    PdfDocument.Page page = pdfDocument.startPage(pageInfo);

                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(Color.WHITE);

                    float centerX = (pageWidth - bitmap.getWidth()) / 2;
                    float centerY = (pageHeight - bitmap.getHeight()) / 2;

                    canvas.drawBitmap(bitmap, centerX, centerY, null);

                    pdfDocument.finishPage(page);
                }
            } catch (Exception e) {
                Log.e("ImagesPdfModule", e.getLocalizedMessage(), e);
                promise.reject("PDF_PAGE_CREATE_ERROR", e.getLocalizedMessage(), e);
                pdfDocument.close();
                return;
            }

            String base64String = null;
            try {
                base64String = writePdfDocument(pdfDocument);
            } catch (Exception e) {
                Log.e("ImagesPdfModule", e.getLocalizedMessage(), e);
                promise.reject("PDF_WRITE_ERROR", e.getLocalizedMessage(), e);
                pdfDocument.close();
                return;
            }

            pdfDocument.close();

            promise.resolve(base64String);

        } catch (Exception e) {
            Log.e("ImagesPdfModule", e.getLocalizedMessage(), e);
            promise.reject("PDF_CREATE_ERROR", e.getLocalizedMessage(), e);
        }
    }

    public String writePdfDocument(PdfDocument pdfDocument) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            pdfDocument.writeTo(outputStream);
        } finally {
            outputStream.close();
        }

        byte[] pdfBytes = outputStream.toByteArray();
        String base64String = Base64.encodeToString(pdfBytes, Base64.DEFAULT);

        return base64String;
    }

    public Bitmap getBitmapFromPathOrUri(String pathOrUri) throws IOException {
        Bitmap bitmap = null;
        InputStream inputStream = null;

        try {
            Uri uri = Uri.parse(pathOrUri);

            String scheme = uri.getScheme();

            if (scheme != null && scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                ContentResolver contentResolver = getReactApplicationContext()
                        .getContentResolver();

                inputStream = contentResolver
                        .openInputStream(uri);
            } else if (scheme == null || scheme.equals(ContentResolver.SCHEME_FILE)) {
                inputStream = new FileInputStream(uri.getPath());
            } else {
                throw new UnsupportedOperationException("Unsupported scheme: " + uri.getScheme());
            }

            if (inputStream != null) {
                bitmap = BitmapFactory
                        .decodeStream(inputStream);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }

        return bitmap;
    }
}
