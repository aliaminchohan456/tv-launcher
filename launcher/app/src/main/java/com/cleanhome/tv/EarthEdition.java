package com.cleanhome.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class EarthEdition {
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final String[] NAMES = {"Makran Coast", "The Red Sea", "Western Australia",
            "The Mediterranean", "The Himalayas", "Baja California", "Namib Coast"};
    // Lower-left coordinates in Web Mercator metres. Each frame has a 16:9 footprint.
    private static final int[][] CORNERS = {{7000000,2700000},{3550000,1950000},
            {12400000,-3100000},{1200000,3950000},{8200000,3260000},
            {-13000000,2620000},{950000,-3050000}};

    final Bitmap image;
    final String place;
    EarthEdition(Bitmap image, String place) {
        this.image = image;
        this.place = place;
    }

    static EarthEdition load(Context context, LocalDate day, int sample) throws IOException {
        File directory = new File(context.getCacheDir(), "earth-editions");
        directory.mkdirs();
        File today = new File(directory, day + ".jpg");
        if (today.isFile() && !hasFullCoverage(today)) today.delete();
        if (!today.isFile()) {
            File pending = new File(directory, "pending");
            try {
                download(day, 3, pending);
                if (!hasFullCoverage(pending)) download(day, 4, pending);
                if (!hasFullCoverage(pending)) throw new IOException("Incomplete satellite coverage");
                if (!pending.renameTo(today)) throw new IOException("Cannot save edition");
                File[] old = directory.listFiles((dir, name) -> name.endsWith(".jpg"));
                if (old != null) for (File file : old) if (!file.equals(today)) file.delete();
            } catch (IOException e) {
                Log.w("EarthEdition", "Daily refresh unavailable", e);
            } finally {
                pending.delete();
            }
        }
        File[] editions = directory.listFiles((dir, name) -> name.endsWith(".jpg"));
        File selected = today.exists() ? today : editions != null && editions.length > 0 ? editions[0] : null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        if (selected != null && hasFullCoverage(selected)) {
            Bitmap bitmap = BitmapFactory.decodeFile(selected.getPath(), options);
            if (bitmap != null) {
                LocalDate edition = LocalDate.parse(selected.getName().substring(0, 10));
                return new EarthEdition(bitmap, NAMES[index(edition)]);
            }
            selected.delete();
        }
        try (InputStream stream = context.getAssets().open("makran.jpg")) {
            return new EarthEdition(BitmapFactory.decodeStream(stream, null, options), NAMES[0]);
        }
    }

    private static int index(LocalDate day) {
        return Math.floorMod((int) day.toEpochDay(), NAMES.length);
    }

    private static void download(LocalDate day, int daysBack, File file) throws IOException {
        int[] corner = CORNERS[index(day)];
        String box = corner[0] + "," + corner[1] + "," + (corner[0]+1500000) + "," + (corner[1]+843750);
        URL url = new URL("https://gibs.earthdata.nasa.gov/wms/epsg3857/best/wms.cgi?SERVICE=WMS"
                + "&VERSION=1.1.1&REQUEST=GetMap&LAYERS=MODIS_Terra_CorrectedReflectance_TrueColor"
                + "&STYLES=&FORMAT=image/jpeg&SRS=EPSG:3857&WIDTH=3840&HEIGHT=2160&BBOX=" + box
                + "&TIME=" + day.minusDays(daysBack));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(10000);
        try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[32768];
            int size = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                size += count;
                if (size > 12_000_000) throw new IOException("Oversized edition");
                out.write(buffer, 0, count);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean hasFullCoverage(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 8;
        Bitmap preview = BitmapFactory.decodeFile(file.getPath(), options);
        if (preview == null) return false;
        try {
            if (preview.getWidth() != 480 || preview.getHeight() != 270) return false;
            // Missing swaths are solid black. Check both axes so even narrow wedges
            // are detected, without rejecting isolated dark terrain pixels.
            int[] vertical = new int[preview.getWidth()];
            int[] row = new int[vertical.length];
            for (int y = 0; y < preview.getHeight(); y++) {
                preview.getPixels(row, 0, row.length, 0, y, row.length, 1);
                int horizontal = 0;
                for (int x = 0; x < row.length; x++) {
                    if ((row[x] & 0x00fcfcfc) == 0) {
                        horizontal++;
                        vertical[x]++;
                        if (horizontal >= 4 || vertical[x] >= 4) return false;
                    } else {
                        horizontal = 0;
                        vertical[x] = 0;
                    }
                }
            }
            return true;
        } finally {
            preview.recycle();
        }
    }
}
