package com.space.myprinter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.pdf.PrintedPdfDocument;
import android.support.v4.print.PrintHelper;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by licht on 2019/1/22.
 */

public class CustomPhotoAdapter extends PrintDocumentAdapter {
    private PrintAttributes mAttributes;
    private String mJobName;
    private int fittingMode = PrintHelper.SCALE_MODE_FIT;
    private boolean mIsMinMarginsHandlingCorrect=false;
    private List<Bitmap> mBitmaps;
    private Context mContext;

    public CustomPhotoAdapter(String jobName,List<Bitmap> bitmaps, Context context) {
        mJobName=jobName;
        mBitmaps = bitmaps;
        mContext = context;
        initMargins();
    }

    private void initMargins() {
        if (Build.VERSION.SDK_INT >= 24) {
            mIsMinMarginsHandlingCorrect=true;
        } else if (Build.VERSION.SDK_INT >= 20) {
            mIsMinMarginsHandlingCorrect=false;
        } else if (Build.VERSION.SDK_INT >= 19) {
            mIsMinMarginsHandlingCorrect=true;
        }
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
        mAttributes = newAttributes;

        PrintDocumentInfo info = new PrintDocumentInfo.Builder(mJobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                .setPageCount(mBitmaps.size())
                .build();
        boolean changed = !newAttributes.equals(oldAttributes);
        callback.onLayoutFinished(info, changed);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal cancellationSignal, WriteResultCallback callback) {
        writeBitmap(mAttributes, fittingMode, mBitmaps, destination,
                cancellationSignal, callback);
    }


    private void writeBitmap(final PrintAttributes attributes, final int fittingMode,
                             final List<Bitmap> bitmaps, final ParcelFileDescriptor fileDescriptor,
                             final CancellationSignal cancellationSignal,
                             final PrintDocumentAdapter.WriteResultCallback writeResultCallback) {
        final PrintAttributes pdfAttributes;
        if (mIsMinMarginsHandlingCorrect) {
            pdfAttributes = attributes;
        } else {
            // If the handling of any margin != 0 is broken, strip the margins and add them to
            // the bitmap later
            pdfAttributes = copyAttributes(attributes)
                    .setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0)).build();
        }

        (new AsyncTask<Void, Void, Throwable>() {
            @Override
            protected Throwable doInBackground(Void... params) {
                try {
                    if (cancellationSignal.isCanceled()) {
                        return null;
                    }

                    PrintedPdfDocument pdfDocument = new PrintedPdfDocument(mContext,
                            pdfAttributes);

                    List<Bitmap>  bitmapList = convertBitmapForColorMode(
                            pdfAttributes.getColorMode());

                    if (cancellationSignal.isCanceled()) {
                        return null;
                    }

                    try {
                        for (int i = 0; i < bitmapList.size(); i++) {
                            PdfDocument.Page page = pdfDocument.startPage(i+1);

                            RectF contentRect;
                            if (mIsMinMarginsHandlingCorrect) {
                                contentRect = new RectF(page.getInfo().getContentRect());
                            } else {
                                // Create dummy doc that has the margins to compute correctly sized
                                // content rectangle
                                PrintedPdfDocument dummyDocument = new PrintedPdfDocument(mContext,
                                        attributes);
                                PdfDocument.Page dummyPage = dummyDocument.startPage(i+1);
                                contentRect = new RectF(dummyPage.getInfo().getContentRect());
                                dummyDocument.finishPage(dummyPage);
                                dummyDocument.close();
                            }

                            // Resize bitmap
                            Matrix matrix = getMatrix(
                                    bitmapList.get(i).getWidth(), bitmapList.get(i).getHeight(),
                                    contentRect, fittingMode);

                            if (mIsMinMarginsHandlingCorrect) {
                                // The pdfDocument takes care of the positioning and margins
                            } else {
                                // Move it to the correct position.
                                matrix.postTranslate(contentRect.left, contentRect.top);

                                // Cut off margins
                                page.getCanvas().clipRect(contentRect);
                            }

                            // Draw the bitmap.
                            page.getCanvas().drawBitmap(bitmapList.get(i), matrix, null);
                            // Finish the page.
                            pdfDocument.finishPage(page);
                        }

                        if (cancellationSignal.isCanceled()) {
                            return null;
                        }

                        // Write the document.
                        pdfDocument.writeTo(
                                new FileOutputStream(fileDescriptor.getFileDescriptor()));
                        return null;
                    } finally {
                        pdfDocument.close();

                        if (fileDescriptor != null) {
                            try {
                                fileDescriptor.close();
                            } catch (IOException ioe) {
                                // ignore
                            }
                        }
                        // If we created a new instance for grayscaling, then recycle it here.
                        for (int i = 0; i < mBitmaps.size(); i++) {
                            if (bitmapList.get(i) != mBitmaps.get(i)) {
                                bitmapList.get(i).recycle();
                            }
                        }

                    }
                } catch (Throwable t) {
                    return t;
                }
            }

            @Override
            protected void onPostExecute(Throwable throwable) {
                if (cancellationSignal.isCanceled()) {
                    // Cancelled.
                    writeResultCallback.onWriteCancelled();
                } else if (throwable == null) {
                    // Done.
                    writeResultCallback.onWriteFinished(
                            new PageRange[] { PageRange.ALL_PAGES });
                } else {
                    // Failed.
                    Log.e("async", "Error writing printed content", throwable);
                    writeResultCallback.onWriteFailed(null);
                }
            }
        }).execute();
    }


    private List<Bitmap> convertBitmapForColorMode(int colorMode) {
        if (colorMode != PrintAttributes.COLOR_MODE_MONOCHROME) {
            return mBitmaps;
        }
        List<Bitmap> grayscales=new ArrayList<>();
        for (int i = 0; i < mBitmaps.size(); i++) {
            // Create a grayscale bitmap
            Bitmap grayscale = Bitmap.createBitmap(mBitmaps.get(i).getWidth(), mBitmaps.get(i).getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(grayscale);
            Paint p = new Paint();
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0);
            ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
            p.setColorFilter(f);
            c.drawBitmap(mBitmaps.get(i), 0, 0, p);
            c.setBitmap(null);
            grayscales.add(grayscale);
        }
        return grayscales;
    }

    private Matrix getMatrix(int imageWidth, int imageHeight, RectF content,
                              int fittingMode) {
        Matrix matrix = new Matrix();

        // Compute and apply scale to fill the page.
        float scale = content.width() / imageWidth;
        if (fittingMode == PrintHelper.SCALE_MODE_FILL) {
            scale = Math.max(scale, content.height() / imageHeight);
        } else {
            scale = Math.min(scale, content.height() / imageHeight);
        }
        matrix.postScale(scale, scale);

        // Center the content.
        final float translateX = (content.width()
                - imageWidth * scale) / 2;
        final float translateY = (content.height()
                - imageHeight * scale) / 2;
        matrix.postTranslate(translateX, translateY);
        return matrix;
    }

    protected PrintAttributes.Builder copyAttributes(PrintAttributes other) {
        PrintAttributes.Builder b = (new PrintAttributes.Builder())
                .setMediaSize(other.getMediaSize())
                .setResolution(other.getResolution())
                .setMinMargins(other.getMinMargins());

        if (other.getColorMode() != 0) {
            b.setColorMode(other.getColorMode());
        }
        return b;
    }
}
