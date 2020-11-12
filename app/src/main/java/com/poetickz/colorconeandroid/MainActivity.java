package com.poetickz.colorconeandroid;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.BitmapCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    // Initialize Variable
    ImageView imageView;
    Button btOpen;
    private  JSONObject respuesta;
    private boolean request_success = false;
    private String job_id;
    private String pictureImagePath = "";
    private int times;
    public final String APP_TAG = "ColorCone";
    public String photoFileName = "photo.jpg";
    private boolean received;
    File photoFile;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Assign Variable
        imageView = findViewById(R.id.image_view);
        btOpen = findViewById(R.id.bt_open);

        // Request camera permission
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{
                            Manifest.permission.CAMERA
                    }, 100);
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 100);
        }


        btOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                photoFile = getPhotoFileUri(photoFileName);
                Uri fileProvider = FileProvider.getUriForFile(MainActivity.this, "com.poetickz.colorconeandroid.fileprovider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider);
                startActivityForResult(cameraIntent, 100);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        request_success = false;
        received = false;
        if (requestCode == 100) {
            //Get Capture Image
            Bitmap captureImage = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            Bitmap.createScaledBitmap(captureImage, 150, 100, true);
            imageView.setImageBitmap(captureImage);

            Log.d("Image Size", String.valueOf(BitmapCompat.getAllocationByteCount(captureImage)));
            final ProgressDialog progDailog = ProgressDialog.show(
                    MainActivity.this, "Processing Image...","Wait...", true);

            new Thread() {
                public void run() {
                    try {
                        processImage(captureImage);
                        while(!request_success){
                            sleep(250);

                        }
                        progDailog.dismiss();
                    } catch (Exception e) {
                    }

                }
            }.start();

        }
    }
    // Returns the File for a photo stored on disk given the fileName
    public File getPhotoFileUri(String fileName) {
        // Get safe storage directory for photos
        // Use `getExternalFilesDir` on Context to access package-specific directories.
        // This way, we don't need to request external read/write runtime permissions.
        File mediaStorageDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), APP_TAG);

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()){
            Log.d(APP_TAG, "failed to create directory");
        }

        // Return the file target for the photo based on filename
        File file = new File(mediaStorageDir.getPath() + File.separator + fileName);

        return file;
    }

    private void processImage(Bitmap image){
        job_id = "";
        String image_to_send = encodeImage(image);
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "https://color-cone-server.herokuapp.com/";



        // request a string response from the provided URL.
        JsonObjectRequest getRequest = new JsonObjectRequest(Request.Method.POST, url, null,
                new Response.Listener<JSONObject>(){
                    @Override
                    public void onResponse(JSONObject response){
                        // Get answer
                        respuesta = response;

                        try {
                            request_success = false;
                            times = 0;
                            job_id = response.getString("job_id");
                            getImage();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            toastError(e.getMessage());
                        }


                        Log.e("DEBUGAPIPOST", response.toString());

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                toastError("Request failed");
                Log.e("DEBUGAPIPOST ERROR", error.toString());
                kill_progressbar();

            }
        }){
            // Set headers
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";//set here instead
            }
            // Make json
            @Override
            public byte[] getBody() {
                try {
                    Map<String, String> params = new HashMap<>();
                    params.put("img", image_to_send);
                    JSONObject json = new JSONObject(params);
                    String requestBody = json.toString();
                    Log.e("f", requestBody);
                    return requestBody == null ? null : requestBody.getBytes("utf-8");
                } catch (UnsupportedEncodingException uee) {
                    return null;
                }
            }
        };

        // add timeout request
        getRequest.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        // add the request to the RequestQueue.
        Log.e("f", "queue");
        queue.add(getRequest);

    }


    private void getImage(){

        String url = "https://color-cone-server.herokuapp.com/results/" + job_id;
        RequestQueue queue = Volley.newRequestQueue(this);
        StringRequest jsonObjReq = new StringRequest(Request.Method.GET,
                url,
                new Response.Listener<String>() {

                    @Override
                    public void onResponse(String response) {
                        Log.d("RESULT", response);
                        if (response.contains("img")){
                            received = true;
                            setImage(response);

                        }else {
                            getImage();
                        }

                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                // Handel Errors
                if (error instanceof TimeoutError || error instanceof NoConnectionError) {
                    Log.e("TAG", error.getMessage());
                } else if (error instanceof AuthFailureError) {
                    Log.e("TAG", error.getMessage());
                } else if (error instanceof ServerError) {
                    Log.e("TAG", error.getMessage());
                } else if (error instanceof NetworkError) {
                    Log.e("TAG", error.getMessage());
                } else if (error instanceof ParseError) {
                    Log.e("PARSEERROR", error.getMessage());
                }
                request_success = true;
                toastError("Something failed :(");
            }
        });

        // Adding request to request queue

        if (!received){
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    queue.add(jsonObjReq);
                }
            }, 500);
        }


    }

    private void setImage(String img_json){
        Log.e("f", "heeey");
        try {

            JSONObject obj = new JSONObject(img_json);

            String img_base_64 = obj.getString("img");

            decode_image(img_base_64);



        } catch (Throwable t) {
            kill_progressbar();
            Log.e("My App", "Could not parse malformed JSON: \"" + img_json + "\"");
        }
    }

    private void decode_image(String b_image){

        byte[] decodedString = Base64.decode(b_image, Base64.DEFAULT);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inDither = false;
        options.inSampleSize = 1;
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length, options);
        imageView.setImageBitmap(image);
        Log.d("Image Size", String.valueOf(BitmapCompat.getAllocationByteCount(image)));
        request_success = true;


    }

    private String encodeImage(Bitmap image){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 15, baos);
        byte[] imageBytes = baos.toByteArray();

        // image to string
        final String imageString = Base64.encodeToString(imageBytes, Base64.DEFAULT);

        return imageString;
    }

    private ProgressDialog startProgressBar(){
        // Progress Bar
        ProgressDialog nDialog = new ProgressDialog(MainActivity.this);
        nDialog.setMessage("Wait...");
        nDialog.setTitle("Processing Image");
        nDialog.setIndeterminate(false);
        nDialog.setCancelable(true);
        return nDialog;
    }

    private void stopProgressBar(ProgressDialog nDialog){
        nDialog.dismiss();
    }

    private void toastError(String message){
        Toast toast = Toast. makeText(getApplicationContext(), message, Toast. LENGTH_SHORT); toast. show();
    }

    private void kill_progressbar(){
        request_success = true;
    }

}