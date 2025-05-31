package com.techbirdssolutions.printcurrentwindow.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class BitmapPrintDocumentAdapter extends PrintDocumentAdapter {

    private Context mContext;
    private Uri mImageUri;
    private int mWidth;
    private int mHeight;
    private Bitmap mBitmap;

    public BitmapPrintDocumentAdapter(Context context, Uri imageUri, int width, int height) {
        mContext = context;
        mImageUri = imageUri;
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            mBitmap = BitmapFactory.decodeStream(mContext.getContentResolver().openInputStream(mImageUri));
        } catch (FileNotFoundException e) {
            Log.e("PrintAdapter", "Failed to open image URI.", e);
        }
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal,
                         LayoutResultCallback callback, Bundle extras) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }

        if (mBitmap != null) {
            PrintDocumentInfo info = new PrintDocumentInfo
                    .Builder("screenshot_print.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build();
            callback.onLayoutFinished(info, true);
        } else {
            callback.onLayoutFailed("Bitmap is null.");
        }
    }

    @Override
    public void onWrite(final PageRange[] pageRanges,
                        final ParcelFileDescriptor destination,
                        final CancellationSignal cancellationSignal,
                        final WriteResultCallback callback) {

        android.graphics.pdf.PdfDocument pdfDocument = new android.graphics.pdf.PdfDocument();
        android.graphics.pdf.PdfDocument.PageInfo pageInfo =
                new android.graphics.pdf.PdfDocument.PageInfo.Builder(mWidth, mHeight, 1).create();

        android.graphics.pdf.PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        page.getCanvas().drawBitmap(mBitmap, 0, 0, null);
        pdfDocument.finishPage(page);

        try (FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
            pdfDocument.writeTo(out);
            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
        } catch (IOException e) {
            Log.e("PrintAdapter", "Error writing PDF.", e);
            callback.onWriteFailed(e.toString());
        } finally {
            pdfDocument.close();
        }
    }

}
