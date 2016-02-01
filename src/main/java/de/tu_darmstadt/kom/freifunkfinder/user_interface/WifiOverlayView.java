package de.tu_darmstadt.kom.freifunkfinder.user_interface;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.List;
import de.tu_darmstadt.kom.freifunkfinder.application.WifiFinderApplication;
import de.tu_darmstadt.kom.freifunkfinder.application.WifiFinderApplicationInt;
import de.tu_darmstadt.kom.freifunkfinder.common.GlobalParams;
import de.tu_darmstadt.kom.freifunkfinder.common.MobileLocation;
import de.tu_darmstadt.kom.freifunkfinder.common.WifiAccessPointDTO;

/**
 * Created by govind,sooraj,puneet on 12/10/2015.
 */
public class WifiOverlayView extends View {

    public static final String DEBUG_TAG = "WifiOverlayView :";
    private final Context context;
    private Handler handler;
    private WifiFinderApplicationInt wifiFinderApplication;
    private Activity activity;

    List<WifiAccessPointDTO> hotSpotses = new ArrayList<WifiAccessPointDTO>();

    private MobileLocationManager mobileLocationManager;
    private WifiFinderApplication wifiFinderApp;
    private MobileSensorManager mobileSensorManager;

    private Location currentLocation = null;
    private Location lastLocation = null;
    private float[] currentAccelerometer = null;
    private float[] currentCompass = null;

    private Display display;
    private float canvasH = 1.0f;
    private float canvasW = 1.0f;

    private float dx = 0.0f;
    private float dy = 0.0f;

    private float orientation[] = new float[3];

    private float verticalFOV;
    private float horizontalFOV;

    private Bitmap hotspotBitmap;
    private Bitmap hotspotSmallGreen;
    private Bitmap hotspotBigGreen;
    private Bitmap hotspotSmallRed;
    private Bitmap hotspotBigRed;
    private Bitmap tickSmall;
    private Bitmap tickBig;
    private Bitmap crossSmall;
    private Bitmap crossBig;
    private Bitmap compassBitmap;

    private Paint compassPaint;
    private Paint compassPaintTxt;
    private Paint compassPaintLin;
    private Paint compassPaintHot;

    public WifiOverlayView(Context context, Activity activity, MobileLocationManager mobileLocationManager) {
        super(context);

        this.context = context;
        this.handler = new Handler();
        this.mobileLocationManager = mobileLocationManager;
        this.activity = activity;
        mobileSensorManager = new MobileSensorManager(context, this);
        mobileSensorManager.initSensors();
        wifiFinderApplication = WifiFinderApplication.getWifiFinderApplication(context);
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        display = windowManager.getDefaultDisplay();

        // get camera parameters for FOV's
        Camera camera = Camera.open();
        Camera.Parameters params = camera.getParameters();
        verticalFOV = params.getVerticalViewAngle();
        horizontalFOV = params.getHorizontalViewAngle();
        camera.release();

        //get the bitmap of hotspot images
        hotspotSmallGreen = BitmapFactory.decodeResource(getResources(), R.drawable.hotspotsmallgreen);
        hotspotBigGreen = BitmapFactory.decodeResource(getResources(), R.drawable.hotspotbigbreen);
        hotspotSmallRed = BitmapFactory.decodeResource(getResources(), R.drawable.hotspotsmallred);
        hotspotBigRed = BitmapFactory.decodeResource(getResources(), R.drawable.hotspotbigred);
        tickSmall = BitmapFactory.decodeResource(getResources(), R.drawable.ticksmall);
        tickBig = BitmapFactory.decodeResource(getResources(), R.drawable.tickbig);
        crossSmall = BitmapFactory.decodeResource(getResources(), R.drawable.crosssmall);
        crossBig = BitmapFactory.decodeResource(getResources(), R.drawable.crossbig);
        compassBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.compass);

        //paint for compass
        compassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        compassPaint.setColor(Color.GRAY);
        compassPaint.setAlpha(60);

        compassPaintLin = new Paint(Paint.ANTI_ALIAS_FLAG);
        compassPaintLin.setColor(Color.GRAY);
        compassPaintLin.setAlpha(200);

        compassPaintTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        compassPaintTxt.setColor(Color.CYAN);
        compassPaintTxt.setTextSize(25);
        compassPaintTxt.setAlpha(100);

        compassPaintHot = new Paint(Paint.ANTI_ALIAS_FLAG);
        compassPaintHot.setColor(Color.GREEN);
        compassPaintHot.setAlpha(80);
    }

    //draw hotspot icons and a compass on the screen
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvasW = canvas.getWidth();
        canvasH = canvas.getHeight();
        float dx_max = canvas.getMaximumBitmapWidth();
        float dy_max = canvas.getMaximumBitmapHeight();

        if (MobileLocation.getLocation() != null) {
            currentLocation = MobileLocation.getLocation();
            if (lastLocation == null) {
                lastLocation = MobileLocation.getLocation();
                loadRelevantHotSpots();
            } else {
                checkAndLoadHotSpots();
            }
        }

        float rotation[] = new float[9];
        float identity[] = new float[9];
        currentAccelerometer = mobileSensorManager.getAccelerometer();
        currentCompass = mobileSensorManager.getCompass();
        if (currentAccelerometer != null && currentCompass != null) {
            boolean isRotation = SensorManager.getRotationMatrix(rotation, identity,
                    currentAccelerometer, currentCompass);
            if (isRotation) {
                float cameraRotation[] = new float[9];

                // remaping for camera pointing straight down the Y axis
                SensorManager.remapCoordinateSystem(rotation,
                        SensorManager.AXIS_X, SensorManager.AXIS_Z,
                        cameraRotation);

                // orientation vector calculation
                SensorManager.getOrientation(cameraRotation, orientation);

                canvas.save();
                // use roll for screen rotation
                canvas.rotate((float) (0.0f - Math.toDegrees(orientation[2])));

                // Translate, also normalize for the FOV of the camera
                dx = (float) ( (canvas.getWidth()/ horizontalFOV) * (Math.toDegrees(orientation[0])));
                dy = (float) ( (canvas.getHeight()/ verticalFOV) * (Math.toDegrees(orientation[1])));

                // translate the dy first so the horizon doesn't get pushed off
                canvas.translate(0.0f, 0.0f - dy);

                // translate the dx
                canvas.translate(0.0f - dx, 0.0f);

                //now draw each hotspot
                if (currentLocation != null) {
                    for (WifiAccessPointDTO tempHotSpots: hotSpotses) {
                        tempHotSpots.setCurrentBearing(currentLocation.bearingTo(tempHotSpots.getLocation()));
                        tempHotSpots.setDx(canvas.getWidth() / 2 +
                                ((float) ((canvas.getWidth() / horizontalFOV) * tempHotSpots.getCurrentBearing())));
                        Double angle = calculateAngle(tempHotSpots.getLocation());
                        tempHotSpots.setDy((canvas.getHeight() / 2) -
                                ((float) ((canvas.getHeight() / verticalFOV) * (angle))));
                        if (tempHotSpots.getDistance() < GlobalParams.getSearchRange()/2) {
                            if (tempHotSpots.isOnline()) {
                                if (tempHotSpots.getLocation().getAltitude() != 0.0) {
                                    canvas.drawBitmap(hotspotBigGreen, tempHotSpots.getDx()-30, tempHotSpots.getDy()-30, null);
                                    canvas.drawBitmap(tickBig, tempHotSpots.getDx()+15, tempHotSpots.getDy()-15, null);
                                } else {
                                    canvas.drawBitmap(hotspotBigGreen, tempHotSpots.getDx()-30, tempHotSpots.getDy()-30, null);
                                    canvas.drawBitmap(crossBig, tempHotSpots.getDx()+15, tempHotSpots.getDy()-15, null);
                                }
                            } else {
                                if (tempHotSpots.getLocation().getAltitude() != 0.0) {
                                    canvas.drawBitmap(hotspotBigRed, tempHotSpots.getDx()-30, tempHotSpots.getDy()-30, null);
                                    canvas.drawBitmap(tickBig, tempHotSpots.getDx()+15, tempHotSpots.getDy()-15, null);
                                } else {
                                    canvas.drawBitmap(hotspotBigRed, tempHotSpots.getDx()-30, tempHotSpots.getDy()-30, null);
                                    canvas.drawBitmap(crossBig, tempHotSpots.getDx()+15, tempHotSpots.getDy()-15, null);
                                }
                            }
                        } else {
                            if (tempHotSpots.isOnline()) {
                                if (tempHotSpots.getLocation().getAltitude() != 0.0) {
                                    canvas.drawBitmap(hotspotSmallGreen, tempHotSpots.getDx()-20, tempHotSpots.getDy()-20, null);
                                    canvas.drawBitmap(tickSmall, tempHotSpots.getDx()+10, tempHotSpots.getDy()-10, null);
                                } else {
                                    canvas.drawBitmap(hotspotSmallGreen, tempHotSpots.getDx()-20, tempHotSpots.getDy()-20, null);
                                    canvas.drawBitmap(crossSmall, tempHotSpots.getDx()+10, tempHotSpots.getDy()-10, null);
                                }
                            } else {
                                if (tempHotSpots.getLocation().getAltitude() != 0.0) {
                                    canvas.drawBitmap(hotspotSmallRed, tempHotSpots.getDx()-20, tempHotSpots.getDy()-20, null);
                                    canvas.drawBitmap(tickSmall, tempHotSpots.getDx()+10, tempHotSpots.getDy()-10, null);
                                } else {
                                    canvas.drawBitmap(hotspotSmallRed, tempHotSpots.getDx()-20, tempHotSpots.getDy()-20, null);
                                    canvas.drawBitmap(crossSmall, tempHotSpots.getDx()+10, tempHotSpots.getDy()-10, null);
                                }
                            }
                        }
                    }
                }

                canvas.restore();

            }
        }

        canvas.save();
        //for compass part
        //draw rectangle
        canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), 120.0f, compassPaint);
        //draw lines
        canvas.drawLine(canvas.getWidth() / 2, 0.0f, canvas.getWidth() / 2, 100.0f, compassPaintLin);
        canvas.drawLine(canvas.getWidth() / 4, 0.0f, canvas.getWidth() / 4, 100.0f, compassPaintLin);
        canvas.drawLine(3 * canvas.getWidth() / 4, 0.0f, 3 * canvas.getWidth() / 4, 100.0f, compassPaintLin);
        canvas.drawLine(0.0f, 0.0f, 0.0f, 100.0f, compassPaintLin);
        canvas.drawLine(canvas.getWidth(), 0.0f, canvas.getWidth(), 100.0f, compassPaintLin);
        //draw s,w,n,e,s
        canvas.drawText("N", (canvas.getWidth() / 2) - 3, 120.0f, compassPaintTxt);
        canvas.drawText("W", (canvas.getWidth() / 4) - 3, 120.0f, compassPaintTxt);
        canvas.drawText("E", (3 * canvas.getWidth() / 4) - 3, 120.0f, compassPaintTxt);
        canvas.drawText("S", (0.0f) - 3, 120.0f, compassPaintTxt);
        //draw hotspots
        if (currentLocation != null) {
            for (WifiAccessPointDTO tempHotSpots : hotSpotses) {
                int width = (int) (canvas.getWidth() / 2 + (((tempHotSpots.getDx()) * canvas.getWidth()) / (2 * dx_max)));
                int hight = (int) (60 + (((tempHotSpots.getDy()) * 120) / (2 * dy_max)));
                canvas.drawCircle(width, hight, 10.0f, compassPaintHot);
            }
        }
        //draw the compass
        //float move = (dx + canvas.getWidth()/2) * canvas.getWidth() / (2 * dx_max);
        float move = (dx) * canvas.getWidth() / (2 * dx_max);
        canvas.drawBitmap(compassBitmap, canvas.getWidth()/2+move-10, 90.0f, null);

        canvas.translate(15.0f, 15.0f);
        canvas.restore();
    }

    //unregister sensor and location calculation
    public void onPause() {
        mobileLocationManager.locationRemove();
        mobileSensorManager.unRegister();
    }

    //restart sensor and location calculation
    public void onResume() {
        mobileLocationManager.initLocation();
        mobileSensorManager.initSensors();
    }

    //calculate angle from height diff and horizontal distance using tan inverse
    private Double calculateAngle(Location loc) {

        Double oppo;
        Double near = haversineDistanceLocal(loc.getLatitude(), loc.getLongitude());
        oppo = (loc.getAltitude() == 0.0) ? 0.0 : (loc.getAltitude() - MobileLocation.getLocation().getAltitude());
        Double angle = Math.toDegrees(Math.atan(oppo / near));
        return angle;
    }


    //local location distance calculation
    private Double haversineDistanceLocal(Double latitude, Double longitude) {

        final int R = 6371;
        Double lat1 = MobileLocation.getLocation().getLatitude();
        Double lon1 = MobileLocation.getLocation().getLongitude();
        Double lat2 = latitude;
        Double lon2 = longitude;

        Double latDistance = toRad(lat2-lat1);
        Double lonDistance = toRad(lon2-lon1);
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
                        Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        Double distance = R * c * 1000;

        return Math.abs(distance);
    }

    private static Double toRad(Double value) {
        return value * Math.PI / 180;
    }


    //on touching a hotspot icon show its details
    @Override
    public boolean onTouchEvent( MotionEvent event) {

        super.onTouchEvent(event);
        Log.d(DEBUG_TAG, "touched");
        float touchX = dx + (event.getX() * canvasW / display.getWidth());
        float touchY = dy + ((float) (event.getY() * canvasH / display.getHeight())) * 1.1f;

        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN)
        {
            return true;
        }

        WifiAccessPointDTO selected = null;
        for (WifiAccessPointDTO tempHotSpots: hotSpotses) {
            if (((tempHotSpots.getDx() + 60) > touchX) && ((tempHotSpots.getDx() - 60) < touchX)
                    && ((tempHotSpots.getDy() + 100) > touchY) && ((tempHotSpots.getDy() - 100) < touchY)) {
                if (selected != null) {
                    if ((Math.abs(tempHotSpots.getDx() - touchX) <= Math.abs(selected.getDx() - touchX))
                            && (Math.abs(tempHotSpots.getDy() - touchY) <= Math.abs(selected.getDy() - touchY))) {
                        selected = tempHotSpots;
                    }
                } else {
                        selected = tempHotSpots;
                }
            }
        }

        if (selected != null) {
            Intent intent = new Intent(context, NodeDescriptionActivity.class);
            intent.putExtra("selected_node", selected);
            activity.startActivity(intent);
        }

        this.invalidate();
        return true;
    }

    //called every time for a 2 meter distance from last location
    private void checkAndLoadHotSpots() {
        Double distance = haversineDistanceLocal(lastLocation.getLatitude(), lastLocation.getLongitude());
        if (distance > 2.0) {
            lastLocation = currentLocation;
            loadRelevantHotSpots();
        }
    }


    //called for loading relevant hotspots
    public void loadRelevantHotSpots() {
        LoadAsyncTask loadAsyncTask = new LoadAsyncTask();
        loadAsyncTask.execute();
    }

    // async inner class for db read operation
    private class LoadAsyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            while (true) {
                if (GlobalParams.isWifiNodesPersisted()) {
                    break;
                }
            }
            hotSpotses = wifiFinderApplication.getRelevantWifiNodes();
            return null;
        }

        protected void onPostExecute() {
            //do nothing
        }

    }

}
